package com.hoppinzq.agent.tool.mcp;

import com.anthropic.client.AnthropicClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * MCP配置加载器
 *
 * 从 resources/mcp.json 文件加载MCP服务器配置
 * 支持多种传输方式的配置
 *
 * 配置格式：
 * {
 *   "mcpServers": {
 *     "服务器名称": {
 *       "command": "命令",           // STDIO方式：启动命令
 *       "args": ["参数1", "参数2"],  // STDIO方式：命令参数
 *       "env": {"KEY": "VALUE"}      // STDIO方式：环境变量（可选）
 *     },
 *     "HTTP服务器": {
 *       "url": "http://...",        // HTTP/SSE方式：服务地址
 *       "headers": {                // HTTP/SSE方式：请求头（可选）
 *         "Authorization": "Bearer xxx"
 *       }
 *     }
 *   }
 * }
 *
 * @author hoppinzq
 */
@Slf4j
public class McpConfigLoader {

    private static final String MCP_CONFIG_FILE = "/mcp.json";
    private final ObjectMapper objectMapper;

    public McpConfigLoader() {
        this.objectMapper = OBJECT_MAPPER;
    }

    /**
     * 从配置文件加载MCP设置
     *
     * @return MCP设置列表
     */
    public List<McpSetting> loadMcpSettings() {
        List<McpSetting> settings = new ArrayList<>();

        try {
            // 读取配置文件
            InputStream inputStream = getClass().getResourceAsStream(MCP_CONFIG_FILE);
            if (inputStream == null) {
                log.warn("未找到配置文件: {}，将使用空配置", MCP_CONFIG_FILE);
                return settings;
            }

            // 解析JSON
            JsonNode rootNode = objectMapper.readTree(inputStream);
            JsonNode serversNode = rootNode.get("mcpServers");

            if (serversNode == null || !serversNode.isObject()) {
                log.warn("配置文件格式错误: 缺少 mcpServers 对象");
                return settings;
            }

            // 遍历服务器配置（键值对形式，键为服务器名称）
            serversNode.fields().forEachRemaining(entry -> {
                String serverName = entry.getKey();
                JsonNode serverConfig = entry.getValue();

                try {
                    // 检查是否启用（支持enabled字段）
                    if (!isEnabled(serverConfig)) {
                        log.info("跳过已禁用的MCP服务器: {}", serverName);
                        return;
                    }

                    McpSetting setting = parseMcpSetting(serverName, serverConfig);
                    settings.add(setting);
                    log.info("加载MCP服务器配置: {} ({})",
                            setting.getName(), setting.getTransportType());

                } catch (Exception e) {
                    log.error("解析MCP服务器配置失败 [{}]: {}", serverName, e.getMessage(), e);
                }
            });

            inputStream.close();

        } catch (Exception e) {
            log.error("加载MCP配置文件失败: {}", e.getMessage(), e);
        }

        return settings;
    }

    /**
     * 解析单个MCP服务器配置
     *
     * @param serverName 服务器名称（作为key）
     * @param serverConfig 服务器配置节点
     * @return MCP设置
     */
    private McpSetting parseMcpSetting(String serverName, JsonNode serverConfig) {
        McpSetting.McpSettingBuilder builder = McpSetting.builder();

        // 基础配置
        builder.mcpId(UUID.randomUUID().toString());
        builder.name(serverName);
        builder.description("MCP服务器: " + serverName);
        builder.clientType(McpSetting.ClientType.SYNC); // 默认使用同步客户端
        builder.timeout(Duration.ofSeconds(10)); // 默认10秒超时

        // 自动判断传输类型
        if (serverConfig.has("command")) {
            // STDIO类型（进程通信）
            builder.transportType(McpSetting.TransportType.STDIO);
            configureStdio(builder, serverConfig);
        } else if (serverConfig.has("url")) {
            // SSE/HTTP类型
            String url = serverConfig.get("url").asText();
            if (url.contains("/sse")) {
                builder.transportType(McpSetting.TransportType.SSE);
            } else {
                builder.transportType(McpSetting.TransportType.Streamable_HTTP);
            }
            configureHttp(builder, serverConfig);
        } else {
            throw new IllegalArgumentException("无法确定传输类型：配置中既没有 'command' 也没有 'url' 字段");
        }

        return builder.build();
    }

    /**
     * 配置STDIO传输方式
     *
     * @param builder 构建器
     * @param configNode 服务器配置节点（包含command、args、env等）
     */
    private void configureStdio(McpSetting.McpSettingBuilder builder, JsonNode configNode) {
        String command = configNode.get("command").asText();

        // 解析参数
        List<String> argsList = new ArrayList<>();
        JsonNode argsNode = configNode.get("args");
        if (argsNode != null && argsNode.isArray()) {
            for (JsonNode arg : argsNode) {
                argsList.add(arg.asText());
            }
        }

        // 解析环境变量
        Map<String, String> envMap = null;
        JsonNode envNode = configNode.get("env");
        if (envNode != null && envNode.isObject()) {
            envMap = objectMapper.convertValue(envNode, Map.class);
        }

        var serverBuilder = ServerParameters.builder(command);

        if (!argsList.isEmpty()) {
            serverBuilder.args(argsList.toArray(new String[0]));
        }

        if (envMap != null && !envMap.isEmpty()) {
            serverBuilder.env(envMap);
        }

        builder.serverParameters(serverBuilder.build());
    }

    /**
     * 配置HTTP/SSE传输方式
     *
     * @param builder 构建器
     * @param configNode 服务器配置节点（包含url、headers等）
     */
    private void configureHttp(McpSetting.McpSettingBuilder builder, JsonNode configNode) {
        String url = configNode.get("url").asText();
        builder.sseUrl(url);

        // 解析headers
        JsonNode headersNode = configNode.get("headers");
        if (headersNode != null && headersNode.isObject()) {
            Map<String, String> headersMap = objectMapper.convertValue(headersNode, Map.class);
            builder.headers(headersMap);

            // 兼容旧的authorization字段
            if (headersMap.containsKey("Authorization")) {
                builder.authorization(headersMap.get("Authorization"));
            }
        }

        // 兼容旧的authorization字段
        String authorization = getText(configNode, "authorization", null);
        if (authorization != null) {
            builder.authorization(authorization);
        }
    }

    /**
     * 检查服务器是否启用（可选功能）
     *
     * @param configNode 服务器配置节点
     * @return 是否启用
     */
    private boolean isEnabled(JsonNode configNode) {
        JsonNode enabledNode = configNode.get("enabled");
        return enabledNode == null || enabledNode.asBoolean(true);
    }

    /**
     * 获取文本字段
     *
     * @param node JSON节点
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    private String getText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : defaultValue;
    }

    /**
     * 获取长整型字段
     *
     * @param node JSON节点
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    private long getLong(JsonNode node, String fieldName, long defaultValue) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asLong() : defaultValue;
    }
}
