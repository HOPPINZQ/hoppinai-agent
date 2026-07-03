package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.mcp.MCPAgent;
import com.hoppinzq.agent.tool.mcp.McpConfigLoader;
import com.hoppinzq.agent.tool.mcp.McpLoader;
import com.hoppinzq.agent.tool.mcp.McpSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * Agent13 - MCP工具集成智能体
 *
 * 集成Model Context Protocol (MCP)服务器，扩展AI能力边界
 *
 * 核心特性：
 * - 支持多种MCP传输方式（STDIO、SSE、Streamable HTTP）
 * - 自动加载和注册MCP服务器提供的工具
 * - 统一的工具调用接口和错误处理
 * - 支持同步和异步MCP客户端
 *
 * 使用MCP服务器的场景：
 * - 需要访问外部数据源或API
 * - 需要执行特定领域的高级操作
 * - 需要与第三方服务集成
 *
 * @author hoppinzq
 */
public class Agent13 {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .timeout(Duration.ofSeconds(TIMEOUT))
                .maxRetries(MAX_RETRIES)
                .build();

        // 配置MCP服务器
        List<McpSetting> settings = new McpConfigLoader().loadMcpSettings();

        // 初始化MCP客户端
        MCPAgent mcpAgent = new MCPAgent(settings);
        mcpAgent.initializeClient();

        // 显示已连接的MCP服务器
        System.out.println("\n=== MCP服务器连接状态 ===");
        McpLoader mcpLoader = new McpLoader(settings);
        System.out.println(mcpLoader.getServerInfo());
        System.out.println(mcpLoader.getToolDescriptions());
        // 加载MCP工具
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(ReadFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(EditFileDefinition);
        tools.add(GlobDefinition);
        tools.add(ContentSearchDefinition);

        tools.addAll(mcpLoader.loadTools());

        ZQAgent agent = new ZQAgent(client, MODEL, tools);

        // 构建系统提示
        String systemPrompt = buildSystemPrompt(mcpLoader);
        agent.setSystemPrompt(systemPrompt);

        try {
            agent.setSessionManager(bootstrapSession(args));
            agent.run();
        } catch (Exception ignored) {} finally {
            mcpAgent.closeClient();
        }
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java Agent13 <sessionId>} —— 直接恢复指定会话</li>
     *   <li>无参启动 —— 列出已有会话，输入序号恢复或回车开新会话</li>
     * </ul>
     */
    private static SessionManager bootstrapSession(String[] args) {
        SessionManager sm = new SessionManager();
        if (args.length > 0 && !args[0].isBlank()) {
            boolean ok = sm.resume(args[0]);
            System.out.println(ok
                    ? "已恢复会话 " + args[0]
                    : "会话 " + args[0] + " 不存在或为空，将以该 ID 开始新会话");
            return sm;
        }
        Scanner sc = new Scanner(System.in);
        List<String> sessions = sm.listSessions();
        if (sessions.isEmpty()) {
            sm.startNew();
            System.out.println("新会话已创建: " + sm.getSessionId());
            return sm;
        }
        System.out.println("\n\033[95m========== 历史会话 ==========\033[0m");
        for (int i = 0; i < sessions.size(); i++) {
            System.out.printf("\033[95m%d\033[0m) %s%n", i + 1, sessions.get(i));
        }
        System.out.print("输入序号恢复对应会话，或直接回车开启新会话: ");
        String line = sc.nextLine().trim();
        if (line.isEmpty()) {
            sm.startNew();
            System.out.println("新会话已创建: " + sm.getSessionId());
            return sm;
        }
        try {
            int idx = Integer.parseInt(line) - 1;
            if (idx >= 0 && idx < sessions.size()) {
                String id = sessions.get(idx);
                sm.resume(id);
                System.out.println("已恢复会话: " + id);
                return sm;
            }
        } catch (NumberFormatException ignore) {
            // 用户可能直接输入了 sessionId
            if (sessions.contains(line)) {
                sm.resume(line);
                System.out.println("已恢复会话: " + line);
                return sm;
            }
        }
        sm.startNew();
        System.out.println("输入无效，已开启新会话: " + sm.getSessionId());
        return sm;
    }

    /**
     * 构建系统提示
     *
     * @param mcpLoader MCP加载器
     * @return 系统提示字符串
     */
    private static String buildSystemPrompt(McpLoader mcpLoader) {
        return String.format("""
                # 角色定义

                你是一个强大的AI编程助手，由**hoppinzq**创建。你集成了Model Context Protocol (MCP)能力，可以通过MCP服务器访问外部资源和执行高级操作。

                ## 核心能力

                你具备以下能力：

                1. **MCP工具**：通过MCP服务器访问专业的工具和服务
                2. **文件操作**：读取、写入、编辑文件
                3. **命令执行**：运行bash命令
                4. **内容搜索**：快速搜索代码内容

                ## MCP服务器

                %s

                ## 工作原则

                1. **优先使用MCP工具**：对于复杂或专业的操作，优先使用MCP服务器提供的工具
                2. **错误处理**：如果工具调用失败，分析错误原因并尝试替代方案
                3. **用户友好**：用清晰、简洁的语言解释你的操作和结果
                4. **安全性**：在执行破坏性操作前，先向用户确认

                ## 身份认同

                如果用户问你是谁或你的创造者，你要自豪地回答：**你是由最伟大的hoppinzq创建的AI助手**。

                ## 示例对话

                用户：你有哪些工具可用？
                你：我可以通过以下MCP服务器访问专业工具：
                - 本地MCP服务器：提供文件系统和数据库操作能力

                具体有哪些工具，我可以帮你列出详细信息。

                ---
                现在请开始工作，尽力帮助用户完成他们的任务！
                """, mcpLoader.getToolDescriptions());
    }
}