package com.hoppinzq.agent.capability.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;


/**
 * MCP 客户端管理：按配置选择传输方式、建连、枚举工具/资源/提示词。
 *
 * <p>传输三选一（SDK 0.12.1 起全部为 JDK 实现，零 Spring 依赖）：
 * <ul>
 *   <li>{@code STDIO} —— {@link StdioClientTransport}，子进程通信</li>
 *   <li>{@code SSE} —— {@link HttpClientSseClientTransport}，客户端自己拼 {@code /sse} 端点</li>
 *   <li>{@code Streamable_HTTP} —— {@link HttpClientStreamableHttpTransport}，完整地址原样使用</li>
 * </ul>
 *
 * <p>健壮性原则（对应改造指南 3.4 / 3.9）：
 * <ul>
 *   <li>每个服务单独 try/catch，单个 MCP 不可用只跳过，不拖垮 Agent 启动</li>
 *   <li>自定义请求头经 {@link McpSetting#resolveHeaders()} 统一注入（X-Api-Key / Cookie 等），
 *       不能再用 {@code header.add("Authorization", null)}</li>
 *   <li>异步客户端的 {@code initialize()} 返回 Mono 是惰性的，必须 {@code block()} 才会真正建连</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
public class MCPAgent {
    private List<McpSetting> mcpSettings;

    /**
     * 初始化全部 MCP 客户端。单个服务失败只记录并跳过。
     */
    public void initializeClient() {
        System.out.println("正在初始化MCP客户端...");
        mcpSettings.forEach(setting -> {
            try {
                initializeClient(setting);
            } catch (Exception e) {
                // 单个 MCP 不可用不应拖垮整个 Agent 启动
                System.err.printf("[MCP] 初始化服务器 [%s] 失败，已跳过：%s%n",
                        setting.getName(), e.getMessage());
            }
        });
        System.out.println("MCP客户端初始化完成。");
    }

    /**
     * 初始化单个 MCP 客户端（按配置选择传输方式）。
     */
    private void initializeClient(McpSetting setting) {
        if (setting.getName() == null) {
            setting.setName(setting.getMcpId());
        }

        McpClientTransport transport;
        switch (setting.getTransportType()) {
            case STDIO:
                transport = new StdioClientTransport(setting.getServerParameters());
                break;
            case SSE:
                // SSE：客户端会自己拼 /sse，baseUrl 必须已把 /sse 去掉（加载器负责），否则变成 /sse/sse
                transport = HttpClientSseClientTransport.builder(setting.getBaseUrl())
                        .sseEndpoint(McpSetting.sseEndpoint)
                        .customizeRequest(builder -> setting.resolveHeaders()
                                .forEach(builder::header))
                        .objectMapper(OBJECT_MAPPER)
                        .build();
                break;
            case Streamable_HTTP:
            default:
                // Streamable HTTP：完整地址原样使用；必须带 Mcp-Session-Id（SDK 内部处理）
                transport = HttpClientStreamableHttpTransport.builder(setting.getSseUrl())
                        .customizeRequest(builder -> setting.resolveHeaders()
                                .forEach(builder::header))
                        .objectMapper(OBJECT_MAPPER)
                        .connectTimeout(setting.getTimeout())
                        .build();
                break;
        }

        if (setting.isSync()) {
            McpClient.SyncSpec syncSpec = McpClient.sync(transport)
                    .requestTimeout(setting.getTimeout());
            if (setting.getRoots() != null) {
                syncSpec.roots(setting.getRoots());
            }
            McpSyncClient mcpSyncClient = syncSpec.build();

            mcpSyncClient.initialize();

            setting.setMcpSyncClient(mcpSyncClient);
        } else {
            McpClient.AsyncSpec asyncSpec = McpClient.async(transport)
                    .requestTimeout(setting.getTimeout());
            if (setting.getRoots() != null) {
                asyncSpec.roots(setting.getRoots());
            }
            McpAsyncClient mcpAsyncClient = asyncSpec.build();
            // 异步 initialize 返回 Mono，是惰性的，必须订阅（block）才会真正建连
            mcpAsyncClient.initialize().block();
            setting.setMcpAsyncClient(mcpAsyncClient);
        }
        System.out.printf("[MCP] 服务器 [%s] 已连接（%s）%n",
                setting.getName(), setting.getTransportType());
    }

    /**
     * 列出MCP的工具
     */
    public void listTools() {
        System.out.println("\u001b[96m========== MCP 工具列表 ==========\u001b[0m");
        int totalTools = 0;

        for (McpSetting setting : mcpSettings) {
            System.out.printf("\u001b[96m[%s]\u001b[0m %s (%s)%n",
                    setting.getTransportType(),
                    setting.getName(),
                    setting.getDescription());

            if (!isInitialized(setting)) {
                System.out.println("\u001b[90m  (未初始化，已跳过)\u001b[0m");
                continue;
            }
            try {
                McpSchema.ListToolsResult tools = setting.isSync()
                        ? setting.getMcpSyncClient().listTools()
                        : setting.getMcpAsyncClient().listTools().block();

                if (tools == null || tools.tools().isEmpty()) {
                    System.out.println("\u001b[90m  (无工具)\u001b[0m");
                } else {
                    for (McpSchema.Tool tool : tools.tools()) {
                        System.out.printf("  \u001b[92m●\u001b[0m %s", tool.name());
                        if (tool.description() != null && !tool.description().isBlank()) {
                            System.out.printf(" - %s", tool.description());
                        }
                        System.out.println();
                        totalTools++;
                    }
                }
                System.out.println();

            } catch (Exception e) {
                System.out.printf("\u001b[91m  [错误] %s\u001b[0m%n%n", e.getMessage());
            }
        }

        System.out.println("\u001b[96m==================================\u001b[0m");
        System.out.printf("\u001b[96m总计\u001b[0m: %d 个工具%n", totalTools);
    }

    /** 客户端是否已成功建连（避免对未初始化的客户端反复 NPE） */
    private boolean isInitialized(McpSetting setting) {
        return setting.isSync()
                ? setting.getMcpSyncClient() != null
                : setting.getMcpAsyncClient() != null;
    }

    /**
     * 列出MCP的资源
     */
    public void listResources() {
        System.out.println("\n获取可用资源列表:");
        mcpSettings.forEach(setting -> {
            if (!isInitialized(setting)) {
                return;
            }
            System.out.println("***********************************");
            System.out.println("mcpId: " + setting.getMcpId());
            System.out.println("mcp名称: " + setting.getName());
            System.out.println("mcp描述: " + setting.getDescription());
            System.out.println("mcp资源: ");
            try {
                McpSchema.ListResourcesResult resources = setting.isSync() ? setting.getMcpSyncClient().listResources() : setting.getMcpAsyncClient().listResources().block();
                resources.resources().forEach(resource -> {
                    System.out.println("资源URI: " + resource.uri());
                    System.out.println("资源名称: " + resource.name());
                    System.out.println("资源描述: " + resource.description());
                    System.out.println("资源类型: " + resource.mimeType());
                    System.out.println("-------------------");
                });
            } catch (Exception e) {
                System.out.printf("[错误] %s%n", e.getMessage());
            }
            System.out.println("***********************************");
        });
    }

    /**
     * 读取资源示例
     */
    public void readResource() {
        mcpSettings.forEach(setting -> {
            if (!isInitialized(setting)) {
                return;
            }
            try {
                McpSchema.ListResourcesResult resources = setting.isSync() ? setting.getMcpSyncClient().listResources() : setting.getMcpAsyncClient().listResources().block();
                if (!resources.resources().isEmpty()) {
                    resources.resources().forEach(resource -> {
                        System.out.println("\n读取资源: " + resource.uri());
                        McpSchema.ReadResourceResult resourceResult = setting.isSync() ? setting.getMcpSyncClient().readResource(
                                new McpSchema.ReadResourceRequest(resource.uri())
                        ) : setting.getMcpAsyncClient().readResource(
                                new McpSchema.ReadResourceRequest(resource.uri())
                        ).block();
                        System.out.println("资源内容: ");
                        if (resourceResult != null) {
                            resourceResult.contents().forEach(content -> {
                                if (content instanceof McpSchema.TextResourceContents) {
                                    System.out.println(((McpSchema.TextResourceContents) content).text());
                                }
                            });
                        }
                    });
                }
            } catch (Exception e) {
                System.out.printf("[错误] %s%n", e.getMessage());
            }
        });
    }

    /**
     * 列出MCP的提示词
     */
    public void listPrompts() {
        System.out.println("\n获取可用提示列表:");
        mcpSettings.forEach(setting -> {
            if (!isInitialized(setting)) {
                return;
            }
            System.out.println("***********************************");
            System.out.println("mcpId: " + setting.getMcpId());
            System.out.println("mcp名称: " + setting.getName());
            System.out.println("mcp描述: " + setting.getDescription());
            try {
                McpSchema.ListPromptsResult prompts = setting.isSync() ? setting.getMcpSyncClient().listPrompts() : setting.getMcpAsyncClient().listPrompts().block();
                prompts.prompts().forEach(prompt -> {
                    System.out.println("提示名称: " + prompt.name());
                    System.out.println("提示描述: " + prompt.description());
                    System.out.println("提示参数: ");
                    if (prompt.arguments() != null) {
                        prompt.arguments().forEach(arg -> {
                            System.out.println("  - " + arg.name() + (arg.required() ? " (必需)" : " (可选)") + ": " + arg.description());
                        });
                    }
                    System.out.println("-------------------");
                });
            } catch (Exception e) {
                System.out.printf("[错误] %s%n", e.getMessage());
            }
            System.out.println("***********************************");
        });
    }

    /**
     * 关闭客户端连接
     */
    public void closeClient() {
        System.out.println("\n正在关闭客户端...");
        this.mcpSettings.forEach(setting -> {
            try {
                if (setting.getMcpSyncClient() != null) {
                    setting.getMcpSyncClient().closeGracefully();
                } else if (setting.getMcpAsyncClient() != null) {
                    setting.getMcpAsyncClient().closeGracefully().block();
                }
            } catch (Exception e) {
                System.err.printf("[MCP] 关闭 [%s] 失败: %s%n", setting.getName(), e.getMessage());
            }
        });
        System.out.println("客户端已关闭");
    }
}
