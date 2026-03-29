package com.hoppinzq.agent.tool.mcp;

import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.tool.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * MCP工具加载器
 *
 * 负责从MCP服务器加载工具并转换为Anthropic SDK可用的ToolDefinition格式
 * 支持同步和异步两种MCP客户端类型
 *
 * 核心功能：
 * - 从MCP设置中加载工具列表
 * - 将MCP工具Schema转换为Anthropic工具Schema
 * - 处理工具调用执行逻辑
 * - 统一异常处理和日志记录
 *
 * @author hoppinzq
 */
@Slf4j
public class McpLoader {

    private final List<McpSetting> mcpSettings;

    public McpLoader(List<McpSetting> mcpSettings) {
        this.mcpSettings = mcpSettings;
    }


    /**
     * 加载所有MCP服务器的工具并转换为ToolDefinition列表
     *
     * @return ToolDefinition列表
     */
    public List<ToolDefinition> loadTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        for (McpSetting setting : mcpSettings) {
            List<ToolDefinition> mcpTools = loadToolsFromSetting(setting);
            tools.addAll(mcpTools);
        }

        log.info("成功加载 {} 个MCP工具", tools.size());
        return tools;
    }

    /**
     * 从单个MCP设置中加载工具
     *
     * @param setting MCP设置
     * @return ToolDefinition列表
     */
    private List<ToolDefinition> loadToolsFromSetting(McpSetting setting) {
        List<ToolDefinition> tools = new ArrayList<>();

        try {
            // 获取MCP工具列表（同步或异步）
            McpSchema.ListToolsResult mcpTools = setting.isSync()
                    ? setting.getMcpSyncClient().listTools()
                    : setting.getMcpAsyncClient().listTools().block();

            // 转换每个工具
            for (McpSchema.Tool mcpTool : mcpTools.tools()) {
                ToolDefinition toolDef = convertToToolDefinition(mcpTool, setting);
                tools.add(toolDef);
            }

            log.debug("从MCP服务器 [{}] 加载了 {} 个工具", setting.getName(), tools.size());

        } catch (Exception e) {
            log.error("从MCP服务器 [{}] 加载工具失败: {}", setting.getName(), e.getMessage(), e);
        }

        return tools;
    }

    /**
     * 将MCP工具转换为ToolDefinition
     * DeepSeek兼容版本: 正确处理required字段位置
     *
     * @param mcpTool MCP工具定义
     * @param setting MCP设置（用于执行时获取客户端）
     * @return ToolDefinition
     */
    private ToolDefinition convertToToolDefinition(McpSchema.Tool mcpTool, McpSetting setting) {
        // 转换输入Schema - 只包含properties
        McpSchema.JsonSchema jsonSchema = mcpTool.inputSchema();
        ObjectNode propertiesNode = OBJECT_MAPPER.valueToTree(jsonSchema.properties());

        // 获取required列表
        List<String> requiredList = jsonSchema.required();

        // 创建工具执行函数
        Function<String, String> executeFunction = createExecuteFunction(setting);

        // 构建InputSchema,将required放在正确的位置
        Tool.InputSchema.Builder schemaBuilder = Tool.InputSchema.builder()
                .properties(com.anthropic.core.JsonValue.fromJsonNode(propertiesNode));

        // 只有当required不为null且不为空时才添加到InputSchema层级
        if (requiredList != null && !requiredList.isEmpty()) {
            schemaBuilder.required(requiredList);
        }

        return ToolDefinition.builder()
                .name(mcpTool.name())
                .description(mcpTool.description())
                .inputSchema(schemaBuilder.build())
                .function(executeFunction)
                .build();
    }

    /**
     * 创建工具执行函数
     *
     * @param setting MCP设置
     * @return 执行函数
     */
    private Function<String, String> createExecuteFunction(McpSetting setting) {
        return new Function<String, String>() {
            @Override
            public String apply(String inputJson) {
                AtomicReference<String> result = new AtomicReference<>("");

                try {
                    // 解析输入参数
                    JsonNode jsonNode = OBJECT_MAPPER.readTree(inputJson);
                    String toolName = jsonNode.get("tool_name").asText();
                    Map<String, Object> arguments = OBJECT_MAPPER.convertValue(
                            jsonNode.get("input"),
                            new TypeReference<Map<String, Object>>() {}
                    );

                    // 调用MCP工具
                    McpSchema.CallToolResult callResult = setting.isSync()
                            ? setting.getMcpSyncClient().callTool(
                                    new McpSchema.CallToolRequest(toolName, arguments))
                            : setting.getMcpAsyncClient().callTool(
                                    new McpSchema.CallToolRequest(toolName, arguments))
                                    .block();

                    // 提取文本内容
                    if (callResult != null && callResult.content() != null) {
                        for (McpSchema.Content content : callResult.content()) {
                            if (content instanceof McpSchema.TextContent) {
                                result.set(((McpSchema.TextContent) content).text());
                            }
                        }
                    }

                    log.debug("MCP工具 [{}] 执行成功", toolName);

                } catch (Exception e) {
                    String errorMsg = String.format("执行MCP工具时异常: %s", e.getMessage());
                    log.error(errorMsg, e);
                    result.set(errorMsg);
                }

                return result.get();
            }
        };
    }

    /**
     * 获取MCP工具描述信息（用于系统提示）
     *
     * @return 工具描述字符串
     */
    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用的MCP工具\n\n");

        for (McpSetting setting : mcpSettings) {
            try {
                McpSchema.ListToolsResult mcpTools = setting.isSync()
                        ? setting.getMcpSyncClient().listTools()
                        : setting.getMcpAsyncClient().listTools().block();

                sb.append(String.format("### %s (%s)\n", setting.getName(), setting.getDescription()));
                sb.append(String.format("MCP ID: `%s`\n\n", setting.getMcpId()));

                for (McpSchema.Tool tool : mcpTools.tools()) {
                    sb.append(String.format("- **%s**: %s\n", tool.name(), tool.description()));
                }
                sb.append("\n");

            } catch (Exception e) {
                log.error("获取MCP服务器 [{}] 的工具描述失败: {}", setting.getName(), e.getMessage());
            }
        }

        return sb.toString();
    }

    /**
     * 获取已加载的MCP服务器信息
     *
     * @return 服务器信息字符串
     */
    public String getServerInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("已连接的MCP服务器:\n\n");

        for (McpSetting setting : mcpSettings) {
            sb.append(String.format("- **%s** (%s)\n", setting.getName(), setting.getDescription()));
            sb.append(String.format("  - 类型: %s\n", setting.getTransportType()));
            sb.append(String.format("  - 客户端: %s\n", setting.getClientType()));
            sb.append(String.format("  - MCP ID: `%s`\n\n", setting.getMcpId()));
        }

        return sb.toString();
    }
}