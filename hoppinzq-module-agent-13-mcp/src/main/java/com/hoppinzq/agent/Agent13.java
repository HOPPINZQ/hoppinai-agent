package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.mcp.McpCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * Agent13 - MCP工具集成智能体
 *
 * <p>集成 Model Context Protocol (MCP) 服务器，扩展 AI 能力边界。
 * 本类只负责<b>装配</b>：服务器配置加载、客户端初始化、MCP 工具转换、
 * {@code /mcp} 命令与提示片段全部收拢在 {@link McpCapability}（capability.mcp 包）。
 *
 * <p>核心特性：
 * <ul>
 *   <li>支持多种 MCP 传输方式（STDIO、SSE、Streamable HTTP）</li>
 *   <li>自动加载并注册 MCP 服务器提供的工具</li>
 *   <li>统一的工具调用接口和错误处理</li>
 * </ul>
 *
 * <p>这就是插拔式集成的完整形态：复制 {@code capability/mcp/} 目录 +
 * 下面 main 中的 {@code agent.capability(mcp)} 一行。
 *
 * @author hoppinzq
 */
public class Agent13 extends ZQAgent {

    public Agent13(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；MCP 工具清单由能力通过 promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root) {
        return String.format("""
                # 角色定义

                你是一个强大的AI编程助手，由**hoppinzq**创建。

                ## 核心能力

                1. **文件操作**：读取、写入、编辑文件
                2. **命令执行**：运行bash命令
                3. **内容搜索**：快速搜索代码内容

                ## 工作原则

                1. **主动使用工具**：根据用户需求主动调用合适的工具
                2. **错误处理**：如果工具调用失败，分析错误原因并尝试替代方案
                3. **用户友好**：用清晰、简洁的语言解释你的操作和结果
                4. **安全性**：在执行破坏性操作前，先向用户确认

                ## 身份认同

                如果用户问你是谁或你的创造者，你要自豪地回答：**你是由最伟大的hoppinzq创建的AI助手**。

                ## 环境

                你工作在 %s 目录下。

                ---
                现在请开始工作，尽力帮助用户完成他们的任务！
                """, root);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 基座工具（MCP 工具由能力在注册时合并进工具表）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, WriteFileDefinition, EditFileDefinition,
                GlobDefinition, ContentSearchDefinition));

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        Agent13 agent = new Agent13(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/mcp/ 目录 + 这一行注册
        // （工具在注册时转换并入表，客户端初始化懒触发，见 McpCapability）
        McpCapability mcp = new McpCapability();
        agent.capability(mcp);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", mcp.promptFragments()));

        System.out.println("=== MCP 工具集成智能体 ===");
        System.out.println(mcp.description());
        System.out.println();

        agent.setSessionManager(sessionManager, new SkillLoader());
        try {
            agent.run();
        } finally {
            // 生命周期收尾：关闭 MCP 连接与 stdio 子进程
            mcp.onStop();
        }
    }
}
