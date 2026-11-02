package com.hoppinzq.agent.capability.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.tool.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * MCP工具加载器：把 MCP 服务器暴露的工具转换为协议中立的 {@link ToolDefinition}。
 *
 * <p>协议兼容要点（对应改造指南 3.7 / 3.9）：
 * <ul>
 *   <li><b>schema 原样保留</b>：type/properties/required 之外，$defs、additionalProperties 等
 *       全部保留——Anthropic 能容忍 schema 缺字段，OpenAI 系端点会校验完整 JSON Schema，
 *       丢了 {@code $ref: "#/$defs/..."} 引用的定义直接 400；{@code "properties": null} 也会被拒</li>
 *   <li><b>工具名清洗</b>：OpenAI 函数名要求 {@code ^[a-zA-Z0-9_-]{1,64}$}，
 *       MCP 工具名里的 {@code . / :} 等字符映射为 {@code _}（执行时经闭包回查原名，无需反向表）</li>
 *   <li><b>双形态入参</b>：兼容 {@code {"tool_name":..., "input":{...}}} 包装与裸参数两种形态，
 *       否则换协议就调不动</li>
 *   <li><b>多文本块拼接</b>：工具结果里的多个 TextContent 拼接而不是后一个覆盖前一个</li>
 *   <li><b>初始化守卫</b>：单个服务建连失败时跳过其工具，不影响其他服务</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Slf4j
public class McpLoader {

    private final List<McpSetting> mcpSettings;

    public McpLoader(List<McpSetting> mcpSettings) {
        this.mcpSettings = mcpSettings;
    }

    /** 客户端是否已成功建连 */
    private boolean isInitialized(McpSetting setting) {
        return setting.isSync()
                ? setting.getMcpSyncClient() != null
                : setting.getMcpAsyncClient() != null;
    }

    /**
     * 加载所有MCP服务器的工具并转换为ToolDefinition列表
     */
    public List<ToolDefinition> loadTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        for (McpSetting setting : mcpSettings) {
            if (!isInitialized(setting)) {
                log.warn("MCP 服务器 [{}] 未初始化成功，跳过工具加载", setting.getName());
                continue;
            }
            tools.addAll(loadToolsFromSetting(setting));
        }

        log.info("成功加载 {} 个MCP工具", tools.size());
        return tools;
    }

    /**
     * 从单个MCP设置中加载工具
     */
    private List<ToolDefinition> loadToolsFromSetting(McpSetting setting) {
        List<ToolDefinition> tools = new ArrayList<>();

        try {
            McpSchema.ListToolsResult mcpTools = setting.isSync()
                    ? setting.getMcpSyncClient().listTools()
                    : setting.getMcpAsyncClient().listTools().block();

            for (McpSchema.Tool mcpTool : mcpTools.tools()) {
                tools.add(convertToToolDefinition(mcpTool, setting));
            }

            log.debug("从MCP服务器 [{}] 加载了 {} 个工具", setting.getName(), tools.size());

        } catch (Exception e) {
            log.error("从MCP服务器 [{}] 加载工具失败: {}", setting.getName(), e.getMessage(), e);
        }

        return tools;
    }

    /**
     * 将MCP工具转换为ToolDefinition。
     * <p>schema 原样序列化并剔除 null 字段；description 缺失时用工具名兜底
     * （OpenAI / Anthropic 的 function description 都不接受 null）。
     */
    private ToolDefinition convertToToolDefinition(McpSchema.Tool mcpTool, McpSetting setting) {
        ObjectNode schema = buildInputSchema(mcpTool.inputSchema());
        // 执行闭包捕获原始工具名：清洗后的名字只用于模型侧分发
        String realToolName = mcpTool.name();
        Function<String, String> executeFunction = inputJson -> callTool(setting, realToolName, inputJson);

        return ToolDefinition.builder()
                .name(sanitizeToolName(mcpTool.name()))
                .description(mcpTool.description() != null ? mcpTool.description() : mcpTool.name())
                .inputSchema(schema)
                .function(executeFunction)
                .build();
    }

    /**
     * OpenAI 函数名限制 {@code ^[a-zA-Z0-9_-]{1,64}$}：
     * MCP 工具名里的非法字符统一映射为 '_'，超长截断。
     * 执行时分发靠 ToolDefinition 的名字匹配（清洗后的），真实调用经闭包用原名，无需反向映射。
     */
    static String sanitizeToolName(String name) {
        String cleaned = name == null ? "unnamed" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    /**
     * 构建协议中立的 InputSchema：完整保留 MCP 返回的 JSON Schema
     * （type/properties/required/additionalProperties/$defs/definitions），
     * 仅剔除 null 字段并补齐 type/properties 兜底。
     */
    private ObjectNode buildInputSchema(McpSchema.JsonSchema jsonSchema) {
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        if (jsonSchema != null) {
            ObjectNode raw = OBJECT_MAPPER.valueToTree(jsonSchema);
            // 剔除 null 字段，避免 OpenAI 兼容端点对 "properties": null 报 400
            raw.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    schema.set(entry.getKey(), entry.getValue());
                }
            });
        }
        if (!schema.hasNonNull("type")) {
            schema.put("type", "object");
        }
        if (!schema.has("properties") || !schema.get("properties").isObject()) {
            schema.set("properties", OBJECT_MAPPER.createObjectNode());
        }
        return schema;
    }

    /**
     * 调用 MCP 工具。兼容两种入参形态：
     * <ul>
     *   <li>{@code {"tool_name": "...", "input": {...}}} —— Agent 内核对无类型工具的包装</li>
     *   <li>{@code {...}} —— 原生 function calling 直接传工具参数</li>
     * </ul>
     */
    private String callTool(McpSetting setting, String realToolName, String inputJson) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(
                    inputJson == null || inputJson.isBlank() ? "{}" : inputJson);

            String toolName = realToolName;
            JsonNode nameNode = jsonNode.get("tool_name");
            if (nameNode != null && nameNode.isTextual() && !nameNode.asText().isBlank()) {
                toolName = nameNode.asText();
            }

            JsonNode inputNode = jsonNode.get("input");
            Map<String, Object> arguments;
            if (inputNode != null) {
                arguments = OBJECT_MAPPER.convertValue(inputNode, new TypeReference<Map<String, Object>>() {});
            } else {
                arguments = OBJECT_MAPPER.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
                arguments.remove("tool_name");
                arguments.remove("input");
            }

            McpSchema.CallToolResult callResult = setting.isSync()
                    ? setting.getMcpSyncClient().callTool(new McpSchema.CallToolRequest(toolName, arguments))
                    : setting.getMcpAsyncClient().callTool(new McpSchema.CallToolRequest(toolName, arguments)).block();

            // 多个文本块拼接，而不是后一个覆盖前一个
            StringBuilder text = new StringBuilder();
            if (callResult != null && callResult.content() != null) {
                for (McpSchema.Content content : callResult.content()) {
                    if (content instanceof McpSchema.TextContent) {
                        if (text.length() > 0) {
                            text.append('\n');
                        }
                        text.append(((McpSchema.TextContent) content).text());
                    }
                }
            }

            log.debug("MCP工具 [{}] 执行成功", toolName);
            return text.toString();

        } catch (Exception e) {
            String errorMsg = String.format("执行MCP工具时异常: %s", e.getMessage());
            log.error(errorMsg, e);
            return errorMsg;
        }
    }

    /**
     * 获取MCP工具描述信息（用于系统提示）
     */
    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        sb.append("### 可用的 MCP 工具\n\n");

        for (McpSetting setting : mcpSettings) {
            if (!isInitialized(setting)) {
                continue;
            }
            try {
                McpSchema.ListToolsResult mcpTools = setting.isSync()
                        ? setting.getMcpSyncClient().listTools()
                        : setting.getMcpAsyncClient().listTools().block();

                sb.append(String.format("#### %s\n", setting.getName()));
                if (setting.getDescription() != null && !setting.getDescription().isBlank()) {
                    sb.append(String.format("*%s*\n\n", setting.getDescription()));
                } else {
                    sb.append("\n");
                }

                for (McpSchema.Tool tool : mcpTools.tools()) {
                    sb.append(String.format("**%s**", sanitizeToolName(tool.name())));
                    if (tool.description() != null && !tool.description().isBlank()) {
                        sb.append(String.format(" — %s", tool.description()));
                    }

                    // 添加参数信息
                    var inputSchema = tool.inputSchema();
                    if (inputSchema != null && inputSchema.properties() != null && !inputSchema.properties().isEmpty()) {
                        sb.append("\n  参数：");
                        var properties = inputSchema.properties();
                        var required = inputSchema.required();
                        boolean first = true;
                        for (var prop : properties.entrySet()) {
                            if (!first) sb.append("、");
                            String propName = prop.getKey();
                            boolean isRequired = required != null && required.contains(propName);
                            sb.append(String.format("`%s`%s", propName, isRequired ? "(必填)" : ""));
                            first = false;
                        }
                    }

                    sb.append("\n\n");
                }

            } catch (Exception e) {
                log.error("获取MCP服务器 [{}] 的工具描述失败: {}", setting.getName(), e.getMessage());
            }
        }

        return sb.toString();
    }

    /**
     * 获取已加载的MCP服务器信息
     */
    public String getServerInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("已连接的MCP服务器:\n\n");

        for (McpSetting setting : mcpSettings) {
            sb.append(String.format("- **%s** (%s)%n", setting.getName(),
                    isInitialized(setting) ? "已连接" : "未初始化（启动时失败，已跳过）"));
            sb.append(String.format("  - 类型: %s%n", setting.getTransportType()));
            sb.append(String.format("  - 客户端: %s%n", setting.getClientType()));
            sb.append(String.format("  - MCP ID: `%s`%n%n", setting.getMcpId()));
        }

        return sb.toString();
    }
}
