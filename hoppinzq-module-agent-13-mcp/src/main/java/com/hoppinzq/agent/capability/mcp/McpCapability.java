package com.hoppinzq.agent.capability.mcp;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.List;

/**
 * MCP（Model Context Protocol）工具集成能力。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>N 个工具：MCP 服务器暴露的工具，经 {@link McpLoader} 转成协议中立的
 *       {@link ToolDefinition}（执行体直接回调 MCP 客户端）</li>
 *   <li>2 个命令：{@code /mcp}（服务器信息）、{@code /mcp tools}（工具列表）
 *       ——原实现在 AgentCommandHandler 里硬编码，现收进能力包</li>
 *   <li>系统提示片段：MCP 工具清单 + 调用约定</li>
 *   <li>生命周期：客户端初始化与关闭</li>
 * </ul>
 *
 * <p>为什么客户端初始化不在 onStart：内核在 {@code agent.capability(...)} 里
 * 先收集 {@code tools()} 再回调 onStart，而 MCP 工具必须在客户端就绪后才能枚举，
 * 故初始化用 {@link #ensureInitialized()} 懒触发，onStart 只负责连接状态横幅。
 *
 * <p>拔除方式：删除本目录 + 入口类的注册行即可，内核不感知本能力。
 *
 * @author hoppinzq
 */
public class McpCapability implements Capability {

    private final McpConfigLoader configLoader;
    private final MCPAgent mcpAgent;
    private final McpLoader mcpLoader;
    /** 懒初始化标记：MCP 客户端只建立一次 */
    private boolean initialized = false;

    public McpCapability() {
        this.configLoader = new McpConfigLoader();
        // 配置只加载一次：MCPAgent（连接管理）与 McpLoader（工具装载）必须共享同一份
        // McpSetting 列表，否则客户端只会初始化到其中一份上
        List<McpSetting> settings = configLoader.loadMcpSettings();
        this.mcpAgent = new MCPAgent(settings);
        this.mcpLoader = new McpLoader(settings);
    }

    @Override
    public String name() {
        return "mcp";
    }

    /** 客户端初始化守卫：tools() 与 onStart 都可能先到，只初始化一次 */
    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        mcpAgent.initializeClient();
    }

    @Override
    public void onStart(ZQAgent agent) {
        ensureInitialized();
        System.out.println("\n=== MCP服务器连接状态 ===");
        System.out.println(mcpLoader.getServerInfo());
    }

    @Override
    public void onStop() {
        // 释放 MCP 连接与子进程（stdio 传输会拉起子进程，必须显式关闭）
        mcpAgent.closeClient();
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        ensureInitialized();
        // MCP 工具转成协议中立的 ToolDefinition，由内核合并进工具表
        return mcpLoader.loadTools();
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(
                new AgentCommand() {
                    @Override
                    public String name() {
                        return McpConfig.COMMAND_PREFIX;
                    }

                    @Override
                    public String description() {
                        return "显示 MCP 服务器信息";
                    }

                    @Override
                    public void execute() {
                        System.out.println("========== MCP 服务器信息 ==========");
                        System.out.print(mcpLoader.getServerInfo());
                    }
                },
                new AgentCommand() {
                    @Override
                    public String name() {
                        return McpConfig.COMMAND_PREFIX + " tools";
                    }

                    @Override
                    public String description() {
                        return "显示 MCP 工具列表";
                    }

                    @Override
                    public void execute() {
                        mcpAgent.listTools();
                    }
                });
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        StringBuilder fragments = new StringBuilder();
        fragments.append("## MCP 工具\n")
                .append("你可以通过 MCP 服务器访问外部资源与专业工具。调用时确保：\n")
                .append("- 传入所有必填参数，参数格式符合工具定义\n")
                .append("- 工具名称和参数大小写准确\n\n");
        // 工具清单在客户端就绪后才有内容；装配顺序保证 promptFragments 晚于 tools()
        fragments.append(mcpLoader.getToolDescriptions());
        return List.of(fragments.toString());
    }

    /** 启动横幅用的能力摘要 */
    public String description() {
        return "MCP 工具集成：从 " + McpConfig.MCP_CONFIG_FILE
                + " 加载服务器配置，支持 STDIO / SSE / Streamable HTTP 传输";
    }
}
