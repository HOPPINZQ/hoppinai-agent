package com.hoppinzq.anthropic.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.hoppinzq.anthropic.constant.AIConstants.OBJECT_MAPPER;

@Data
@AllArgsConstructor
public class MCPAgent {
    private List<McpSetting> mcpSettings;

    /**
     * 初始化并返回一个MCP同步客户端
     * 该方法会创建客户端传输层，配置同步客户端参数（包括10秒的请求超时），
     * 然后初始化客户端连接并返回配置好的客户端实例。
     */
    public void initializeClient() {
        System.out.println("正在初始化MCP同步客户端...");
        mcpSettings.forEach(setting -> {
            if (setting.getName() == null) {
                setting.setName(setting.getMcpId());
            }
            McpClientTransport transport = null;
            switch (setting.getTransportType()){
                case STDIO:
                    transport = new StdioClientTransport(setting.getServerParameters());

                    break;
                case SSE:
                    // 0.9.0及以上版本的sse传输方式已被废弃，使用Streamable_HTTP替代
//                    transport = new HttpClientSseClientTransport(
//                            HttpClient.newBuilder(),
//                            setting.getSseUrl(),
//                            McpSetting.sseEndpoint,
//                            OBJECT_MAPPER);
                case Streamable_HTTP:
                    transport = new WebFluxSseClientTransport(
                            WebClient.builder()
                                    .baseUrl(setting.getSseUrl())
                                    // 请求设置、自定义请求头、cookie支持等自行拓展
                                    .defaultHeaders(header -> header.add("Authorization", setting.getAuthorization()))
                            ,
                            OBJECT_MAPPER,McpSetting.sseEndpoint);
                    break;
                default:
                    throw new RuntimeException("不支持的传输方式："+setting.getTransportType());
            }

            if (setting.isSync()) {
                McpClient.SyncSpec syncSpec = McpClient.sync(transport)
                        .requestTimeout(setting.getTimeout());
                if (setting.getRoots() != null) {
                    syncSpec.roots(setting.getRoots());
                }
                // 其他mcp能力待补充
                McpSyncClient mcpSyncClient = syncSpec.build();

                mcpSyncClient.initialize();

                setting.setMcpSyncClient(mcpSyncClient);
            } else {
                McpClient.AsyncSpec asyncSpec = McpClient.async(transport)
                        .requestTimeout(setting.getTimeout());
                if (setting.getRoots() != null) {
                    asyncSpec.roots(setting.getRoots());
                }
                // 其他mcp能力待补充
                McpAsyncClient mcpAsyncClient = asyncSpec.build();
                mcpAsyncClient.initialize();
                setting.setMcpAsyncClient(mcpAsyncClient);
            }
        });
        System.out.println("同步MCP客户端初始化完成。");
    }

    /**
     * 列出MCP的工具
     */
    public void listTools() {
        System.out.println("\n获取可用工具列表:");
        mcpSettings.forEach(setting -> {
            System.out.println("\n***********************************");
            System.out.println("mcpId: " + setting.getMcpId());
            System.out.println("mcp名称: " + setting.getName());
            System.out.println("mcp描述: " + setting.getDescription());
            System.out.println("mcp工具: ");
            McpSchema.ListToolsResult tools = setting.isSync() ? setting.getMcpSyncClient().listTools() : setting.getMcpAsyncClient().listTools().block();
            tools.tools().forEach(tool -> {
                System.out.println("\t工具名称: " + tool.name());
                System.out.println("\t工具描述: " + tool.description());
                System.out.println("\t工具Schema: " + tool.inputSchema());
                System.out.println("\t-------------------");
            });
            System.out.println("***********************************");
        });
    }

    /**
     * 列出MCP的资源
     */
    public void listResources() {
        System.out.println("\n获取可用资源列表:");
        mcpSettings.forEach(setting -> {
            System.out.println("***********************************");
            System.out.println("mcpId: " + setting.getMcpId());
            System.out.println("mcp名称: " + setting.getName());
            System.out.println("mcp描述: " + setting.getDescription());
            System.out.println("mcp工具: ");
            McpSchema.ListResourcesResult resources = setting.isSync() ? setting.getMcpSyncClient().listResources() : setting.getMcpAsyncClient().listResources().block();
            resources.resources().forEach(resource -> {
                System.out.println("资源URI: " + resource.uri());
                System.out.println("资源名称: " + resource.name());
                System.out.println("资源描述: " + resource.description());
                System.out.println("资源类型: " + resource.mimeType());
                System.out.println("-------------------");
            });
            System.out.println("***********************************");
        });
    }

    /**
     * 读取资源示例
     */
    public void readResource() {
        mcpSettings.forEach(setting -> {
            McpSchema.ListResourcesResult resources = setting.isSync() ? setting.getMcpSyncClient().listResources() : setting.getMcpAsyncClient().listResources().block();
            if (!resources.resources().isEmpty()) {
                resources.resources().forEach(resource -> {
                    System.out.println("\n读取资源: " + resource.uri());
                    McpSchema.ReadResourceResult resourceResult = setting.isSync()?setting.getMcpSyncClient().readResource(
                            new McpSchema.ReadResourceRequest(resource.uri())
                    ):setting.getMcpAsyncClient().readResource(
                            new McpSchema.ReadResourceRequest(resource.uri())
                    ).block();
                    System.out.println("资源内容: ");
                    if(resourceResult!=null){
                        resourceResult.contents().forEach(content -> {
                            if (content instanceof McpSchema.TextResourceContents) {
                                System.out.println(((McpSchema.TextResourceContents) content).text());
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * 列出MCP的提示词
     */
    public void listPrompts() {
        System.out.println("\n获取可用提示列表:");
        mcpSettings.forEach(setting -> {
            System.out.println("***********************************");
            System.out.println("mcpId: " + setting.getMcpId());
            System.out.println("mcp名称: " + setting.getName());
            System.out.println("mcp描述: " + setting.getDescription());
            System.out.println("mcp工具: ");
            McpSchema.ListPromptsResult prompts = setting.isSync() ? setting.getMcpSyncClient().listPrompts() : setting.getMcpAsyncClient().listPrompts().block();
            prompts.prompts().forEach(prompt -> {
                System.out.println("提示名称: " + prompt.name());
                System.out.println("提示描述: " + prompt.description());
                System.out.println("提示参数: ");
                prompt.arguments().forEach(arg -> {
                    System.out.println("  - " + arg.name() + (arg.required() ? " (必需)" : " (可选)") + ": " + arg.description());
                });
                System.out.println("-------------------");
            });
            System.out.println("***********************************");
        });
    }

    /**
     * 关闭客户端连接
     * 该方法会优雅地关闭MCP同步客户端，并在控制台输出关闭状态信息。
     * 执行流程：
     * 1. 打印关闭开始提示
     * 2. 调用客户端的优雅关闭方法
     * 3. 打印关闭完成提示
     */
    public void closeClient() {
        System.out.println("\n正在关闭客户端...");
        this.mcpSettings.forEach(setting -> {
            if (setting.getMcpSyncClient() != null) {
                setting.getMcpSyncClient().closeGracefully();
            } else if (setting.getMcpAsyncClient() != null) {
                setting.getMcpAsyncClient().closeGracefully();
            }
        });
        System.out.println("客户端已关闭");
    }
}
