package com.hoppinzq.agent.capability.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * mcp.json 中单个 MCP 服务器的配置模型。
 *
 * <p>请求头三级配置（优先级从高到低，见 {@link #resolveHeaders()}）：
 * <ol>
 *   <li>{@code headers} —— 任意自定义请求头 Map（X-Api-Key、Cookie 等都能配）</li>
 *   <li>{@code headerName}/{@code headerValue} —— 单请求头便捷写法</li>
 *   <li>{@code authorization} —— 旧字段，等价于 headers 中的 Authorization</li>
 * </ol>
 *
 * <p>{@code @Builder.Default} 不能省：否则用 builder 构建时字段初始值会被忽略
 * （{@code headers} 会变成 null，下游遍历直接 NPE）。
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class McpSetting {
    public static final String sseEndpoint = "/sse";

    // 动态计算的 baseUrl：SSE 时 URL 已包含 /sse 则去掉（客户端会自己拼，否则变成 /sse/sse）
    private String baseUrl;

    private String mcpId;
    private String name;
    private String description;

    @Builder.Default
    private ClientType clientType = ClientType.SYNC;

    // 读配置文件（mcp.json 的 type 字段）：stdio / sse / streamable-http；缺省 Streamable_HTTP
    @Builder.Default
    private TransportType transportType = TransportType.Streamable_HTTP;

    private McpSyncClient mcpSyncClient;
    private McpAsyncClient mcpAsyncClient;
    private Duration timeout;
    // 标准输入输出配置（STDIO 传输的子进程命令与参数）
    private ServerParameters serverParameters;
    // Streamable HTTP 用的完整端点地址（原样使用）；SSE 时保留原始 URL 用于日志
    private String sseUrl;

    // 旧字段：等价于 headers 中的 Authorization
    private String authorization;
    // 主要配置：任意自定义请求头
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();
    // 便捷配置：单个请求头
    private String headerName;
    private String headerValue;

    // client 能力：根、采样、日志等的配置
    private List<McpSchema.Root> roots;

    public Boolean isSync() {
        return this.getClientType() == ClientType.SYNC;
    }

    /**
     * 合并实际发送的请求头。优先级：headers &gt; headerName/headerValue &gt; authorization。
     * <p>过滤 null：{@code HttpHeaders} / JDK HttpClient 的 header 都不接受 null 值。
     */
    public Map<String, String> resolveHeaders() {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (authorization != null && !authorization.isBlank()) {
            resolved.put("Authorization", authorization.trim());
        }
        if (headerName != null && !headerName.isBlank() && headerValue != null) {
            resolved.put(headerName.trim(), headerValue);
        }
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    resolved.put(key.trim(), value);
                }
            });
        }
        return resolved;
    }

    public enum ClientType {
        SYNC,
        ASYNC
    }

    public enum TransportType {
        STDIO,
        SSE,
        Streamable_HTTP
    }
}
