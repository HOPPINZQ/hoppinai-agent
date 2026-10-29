package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.sandbox.SandboxCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;

/**
 * s23 安全沙箱智能体。
 *
 * <p>本类只负责<b>装配</b>：四层沙箱（L1 静态分析 → L2 目录监禁 → L3 OS 沙箱 →
 * L4 容器隔离 → 降级带超时 ProcessBuilder）、bash 工具、beforeToolCall 路径越界检查、
 * 提示词片段全部收拢在 {@link SandboxCapability}（capability.sandbox 包），
 * 入口类不再覆写任何循环方法。
 *
 * <p>这就是插拔式集成的完整形态：复制 {@code capability/sandbox/} 目录 +
 * 下面 main 中的一行 {@code agent.capability(new SandboxCapability())}。
 *
 * <p>示例提示词：
 * <ul>
 *   <li>"执行 rm -rf /tmp/test"（L1 静态分析应拦截）</li>
 *   <li>"列出 ../../etc 下的文件"（L2 目录监禁应拦截）</li>
 *   <li>"echo hello"（通过 L1+L2，降级到带超时 ProcessBuilder 执行）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class Agent23 extends ZQAgent {

    public Agent23(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；能力通过 promptFragments 贡献自己的部分）。
     */
    public static String generateSystemPrompt(String root) {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                # 角色定义

                你是一个安全沙箱 AI 编程助手，由 hoppinzq 创建。

                ## 当前环境

                - 操作系统：%s
                - 工作目录（监禁根）：%s

                ## 身份

                你是由最伟大的 hoppinzq 创建的 AI 助手。""", osName, root);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        // 基座工具列表为空：bash 是沙箱特性的载体，由能力注册
        List<ToolDefinition> baseTools = new ArrayList<>();

        // 插拔式集成：复制 capability/sandbox/ 目录 + 这一行注册
        SandboxCapability sandbox = new SandboxCapability();
        Agent23 agent = new Agent23(provider, MODEL, baseTools);
        agent.capability(sandbox);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", sandbox.promptFragments()));

        System.out.println("=== s23 安全沙箱智能体 ===");
        System.out.println(sandbox.description());
        System.out.println();

        // 本模块无技能体系，SkillLoader 传 null（/skills 命令会提示未启用）
        agent.setSessionManager(sessionManager, null);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
