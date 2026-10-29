package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.hooks.HooksCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 钩子系统示例（对应 Python 教程 s04_hooks）。
 *
 * <p>本类只负责<b>装配</b>：4 个示例钩子（危险命令拦截 / 审计日志 / 长输出告警 / 每轮统计）
 * 全部收拢在 {@link HooksCapability}（capability.hooks 包），通过内核的
 * {@code AgentHook} 契约挂到 {@code beforeToolCall} / {@code afterToolCall} / {@code afterLlmResponse}。
 *
 * <p>原实现自建了一套 HookEvent/HookCallback/HookRegistry 框架，与本契约重复，已删除 ——
 * 能力包对内核的依赖仅剩契约本身。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"执行 rm -rf /tmp/x"   → DangerousCommandHook 应阻断</li>
 *   <li>"列出当前目录所有文件" → 正常执行，AuditLogHook 打印审计，TurnSummaryHook 统计次数</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentHooks extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    public AgentHooks(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；钩子规则由 HooksCapability.promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root, String skillDescriptions) {
        return String.format("""
                        你是一个编程智能体，工作在目录: %s

                        核心能力：
                        - 使用工具读写文件、编辑代码、执行命令、搜索内容
                        - 通过 todo 工具管理任务列表，追踪复杂任务进度
                        - 加载专业技能以应对特定领域的任务

                        工作指南：
                        1. 优先使用工具解决任务，而非仅提供建议
                        2. 在处理复杂或不熟悉的主题时，使用 load_skill 工具获取专业知识
                        3. 使用 todo 工具将大任务分解为小步骤，逐个完成

                        可用的技能：
                        %s""",
                root, skillDescriptions);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        SessionManager sessionManager = SessionManager.bootstrap(args);

        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition, SubAgentDefinition, TodoDefinition,
                SkillsDefinition));

        AgentHooks agent = new AgentHooks(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/hooks/ 目录 + 这一行注册
        HooksCapability hooks = new HooksCapability();
        agent.capability(hooks);

        agent.setSystemPrompt(generateSystemPrompt(ROOT, skillLoader.getDescriptions()) + "\n\n\n"
                + String.join("\n", hooks.promptFragments()));

        System.out.println("=== 钩子系统智能体 ===");
        System.out.println(hooks.description());
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        agent.run();
    }
}
