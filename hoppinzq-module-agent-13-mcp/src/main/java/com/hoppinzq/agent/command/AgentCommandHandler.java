package com.hoppinzq.agent.command;

import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.mcp.MCPAgent;
import com.hoppinzq.agent.tool.mcp.McpLoader;
import lombok.Setter;

/**
 * Agent 特殊命令处理器。
 * <p>处理以 {@code /} 开头的用户命令，不发送给 LLM。
 * <p>内置命令：
 * <ul>
 *   <li>{@code /stats} —— 打印当前会话的 token 统计</li>
 *   <li>{@code /usage} —— 打印每次 LLM 调用的 token 明细</li>
 *   <li>{@code /mcp} —— 显示 MCP 服务器信息</li>
 *   <li>{@code /mcp tools} —— 显示 MCP 工具列表</li>
 *   <li>{@code /exit} —— 退出程序并打印统计</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentCommandHandler {

    private final SessionManager sessionManager;

    @Setter
    private MCPAgent mcpAgent;
    @Setter
    private McpLoader mcpLoader;

    public AgentCommandHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 判断输入是否为特殊命令。
     *
     * @param input 用户输入
     * @return 若以 {@code /} 开头返回 {@code true}
     */
    public boolean isCommand(String input) {
        return input != null && input.startsWith("/");
    }

    /**
     * 执行特殊命令。
     *
     * @param input 用户输入（以 {@code /} 开头）
     * @return 若命令已处理返回 {@code true}；若为未知命令返回 {@code false}
     */
    public boolean handleCommand(String input) {
        if (!isCommand(input)) {
            return false;
        }

        String cmd = input.toLowerCase();

        // 基础命令
        switch (cmd) {
            case "/stats":
                handleStats();
                return true;
            case "/usage":
                handleUsage();
                return true;
            case "/exit":
                handleExit();
                return true;
            case "/mcp":
                handleMcp();
                return true;
            case "/mcp tools":
                handleMcpTools();
                return true;
        }

        // 检查是否是 MCP 相关命令
        if (cmd.startsWith("/mcp")) {
            handleMcp();
            return true;
        }

        System.out.println("\u001b[90m[提示] 未知命令: " + input + "\u001b[0m");
        printHelp();
        return true;
    }

    /**
     * 打印帮助信息
     */
    private void printHelp() {
        System.out.println("\u001b[90m可用命令:\u001b[0m");
        System.out.println("  /stats   - 打印当前会话的 token 统计");
        System.out.println("  /usage   - 打印每次 LLM 调用的 token 明细");
        System.out.println("  /mcp     - 显示 MCP 服务器信息");
        System.out.println("  /mcp tools - 显示 MCP 工具列表");
        System.out.println("  /exit    - 退出程序并打印统计");
    }

    /**
     * 处理 MCP 服务器信息命令
     */
    private void handleMcp() {
        if (mcpLoader == null) {
            System.out.println("\u001b[90m[提示] 本会话未启用 MCP 功能\u001b[0m");
            return;
        }
        System.out.println("\u001b[96m========== MCP 服务器信息 ==========\u001b[0m");
        System.out.print(mcpLoader.getServerInfo());
    }

    /**
     * 处理 MCP 工具列表命令
     */
    private void handleMcpTools() {
        if (mcpAgent == null) {
            System.out.println("\u001b[90m[提示] 本会话未启用 MCP 功能\u001b[0m");
            return;
        }
        mcpAgent.listTools();
    }

    private void handleStats() {
        if (sessionManager != null) {
            sessionManager.printSummary();
        } else {
            System.out.println("\u001b[90m[提示] 本会话未启用 session 管理器，无统计数据\u001b[0m");
        }
    }

    private void handleUsage() {
        if (sessionManager == null) {
            System.out.println("\u001b[90m[提示] 本会话未启用 session 管理器，无统计数据\u001b[0m");
            return;
        }
        var usageList = sessionManager.getUsageList();
        if (usageList.isEmpty()) {
            System.out.println("\u001b[90m[提示] 本次会话暂无 token 记录\u001b[0m");
            return;
        }

        System.out.println("\u001b[90m========== Token 使用明细 ==========\u001b[0m");
        long totalInput = 0, totalOutput = 0, totalCache = 0;
        int idx = 1;
        for (var u : usageList) {
            long input = u.getInputTokens() != null ? u.getInputTokens() : 0;
            long output = u.getOutputTokens() != null ? u.getOutputTokens() : 0;
            long cache = u.getCacheReadTokens() != null ? u.getCacheReadTokens() : 0;
            totalInput += input;
            totalOutput += output;
            totalCache += cache;

            String time = u.getTimestamp() != null ? u.getTimestamp().substring(11, 19) : "--:--:--";
            System.out.printf("\u001b[90m[%d]\u001b[0m %s  输入:\u001b[90m%d\u001b[0m  输出:\u001b[90m%d\u001b[0m  缓存:\u001b[90m%d\u001b[0m%n",
                    idx++, time, input, output, cache);
        }
        System.out.println("\u001b[90m==================================\u001b[0m");
        System.out.printf("\u001b[90m总计\u001b[0m: 输入:\u001b[90m%d\u001b[0m  输出:\u001b[90m%d\u001b[0m  缓存:\u001b[90m%d\u001b[0m%n",
                totalInput, totalOutput, totalCache);
    }

    private void handleExit() {
        if (sessionManager != null) {
            sessionManager.printSummary();
        }
        System.out.println("再见！");
        System.exit(0);
    }
}
