package com.hoppinzq.agent.capability.mcp;

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
            InputStream inputStream = getClass().getResourceAsStream(McpConfig.MCP_CONFIG_FILE);
            if (inputStream == null) {
                log.warn("未找到配置文件: {}，将使用空配置", McpConfig.MCP_CONFIG_FILE);
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
        // 支持从配置读取超时，默认60秒
        long timeoutSeconds = getLong(serverConfig, "timeout", 60);
        builder.timeout(Duration.ofSeconds(timeoutSeconds));

        // 传输类型：显式 type 字段优先；旧配置（无 type）按 command/url 推断
        McpSetting.TransportType transportType = resolveTransportType(serverConfig);
        builder.transportType(transportType);

        switch (transportType) {
            case STDIO:
                // 必填 command，解析 args/env
                configureStdio(builder, serverConfig);
                break;
            case SSE:
                // SSE：客户端会自己拼 /sse，baseUrl 必须把 /sse 去掉，否则变成 /sse/sse
                String sseUrl = requireUrl(serverConfig);
                String baseUrl = sseUrl.substring(0, sseUrl.lastIndexOf("/sse"));
                builder.baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
                builder.sseUrl(sseUrl);
                configureHttp(builder, serverConfig);
                break;
            case Streamable_HTTP:
            default:
                // Streamable HTTP：完整地址原样使用
                String url = requireUrl(serverConfig);
                builder.baseUrl(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
                builder.sseUrl(url);
                configureHttp(builder, serverConfig);
                break;
        }

        return builder.build();
    }

    /**
     * 解析传输类型。
     * <p>显式 {@code type} 字段优先（stdio / sse / http / streamable-http，大小写与下划线不敏感）；
     * 旧配置没有 type 时按 command/url 推断（url 含 /sse 视为 SSE，否则 Streamable HTTP）。
     */
    private McpSetting.TransportType resolveTransportType(JsonNode serverConfig) {
        String declaredType = getText(serverConfig, "type", null);
        if (declaredType != null && !declaredType.isBlank()) {
            String normalized = declaredType.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            switch (normalized) {
                case "stdio":
                    return McpSetting.TransportType.STDIO;
                case "sse":
                    return McpSetting.TransportType.SSE;
                case "http":
                case "streamable-http":
                case "streamablehttp":
                    return McpSetting.TransportType.Streamable_HTTP;
                default:
                    throw new IllegalArgumentException("不支持的 type 配置：" + declaredType
                            + "（可选值：stdio / sse / streamable-http）");
            }
        }
        // 兼容旧配置：未写 type 时按 command/url 推断
        if (serverConfig.has("command")) {
            return McpSetting.TransportType.STDIO;
        }
        if (serverConfig.has("url")) {
            String url = serverConfig.get("url").asText();
            return url.contains("/sse")
                    ? McpSetting.TransportType.SSE
                    : McpSetting.TransportType.Streamable_HTTP;
        }
        throw new IllegalArgumentException("无法确定传输类型：请配置 type 字段，或提供 'command' / 'url'");
    }

    /** 取必填的 url 字段 */
    private String requireUrl(JsonNode serverConfig) {
        JsonNode urlNode = serverConfig.get("url");
        if (urlNode == null || urlNode.isNull() || urlNode.asText().isBlank()) {
            throw new IllegalArgumentException("该传输类型要求配置 url 字段");
        }
        return urlNode.asText();
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
     * 配置HTTP/SSE传输方式的请求头与超时。
     *
     * @param builder 构建器
     * @param configNode 服务器配置节点（包含headers / headerName / headerValue / authorization / timeout）
     */
    private void configureHttp(McpSetting.McpSettingBuilder builder, JsonNode configNode) {
        // baseUrl 和 sseUrl 已在 parseMcpSetting 中设置，这里只处理 headers / 超时

        // 解析headers（任意自定义请求头：X-Api-Key、Cookie 等）
        JsonNode headersNode = configNode.get("headers");
        if (headersNode != null && headersNode.isObject()) {
            Map<String, String> headersMap = objectMapper.convertValue(headersNode, Map.class);
            builder.headers(headersMap);

            // 兼容旧的authorization字段
            if (headersMap.containsKey("Authorization")) {
                builder.authorization(headersMap.get("Authorization"));
            }
        }
        // 单请求头便捷写法
        builder.headerName(getText(configNode, "headerName", null));
        builder.headerValue(getText(configNode, "headerValue", null));

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
