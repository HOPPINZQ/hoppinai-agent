package com.hoppinzq.anthropic.mcp;

import com.anthropic.client.AnthropicClient;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

/**
 * mcp 配置类，待拓展
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class McpSetting {
    public static final String sseEndpoint = "/sse";

    private String mcpId;
    private String name;
    private String description;
    private ClientType clientType = ClientType.SYNC;
    private TransportType transportType = TransportType.STDIO;
    private McpSyncClient mcpSyncClient;
    private McpAsyncClient mcpAsyncClient;
    private Duration timeout;
    // 标准输入输出配置
    private ServerParameters serverParameters;
    // SSE配置
    private String sseUrl;
    // 请求头配置，就写了一个，自己拓展
    private String authorization;

    // client 能力，根、采样、日志等的配置
    private List<McpSchema.Root> roots;
    // anthropic 配置，大模型配置，可以以不同的大模型使用不同的mcp server
    private AnthropicClient anthropicClient;


    public Boolean isSync(){
        return this.getClientType() == ClientType.SYNC;
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