package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.subagent.SubAgentCapability;
import com.hoppinzq.agent.capability.compact.CompactCapability;
import com.hoppinzq.agent.capability.skill.SkillCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 上下文压缩智能体。
 *
 * <p>本类只负责<b>装配</b>：压缩策略（L1~L4）、命令、工具、提示词片段全部收拢在
 * {@link CompactCapability}（capability.compact 包），Agent 每轮的压缩触发点
 * 由内核的 beforeLlmCall 钩子分发，入口类不再覆写任何循环方法。
 *
 * <p>这就是插拔式集成的完整形态：复制 {@code capability/compact/} 目录 +
 * 下面 main 中的一行 {@code agent.capability(new CompactCapability(...))}。
 *
 * @author hoppinzq
 */
public class Agent06 extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取该实例，
     * 必须在构建工具列表之前赋值。
     */
    public static TodoManager todoManager;

    public Agent06(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；能力通过 promptFragments 贡献自己的部分）。
     */
    public static String generateSystemPrompt(String root) {
        return String.format(
                "你是一个编程智能体，工作在目录: %s\n\n" +
                "核心能力：\n" +
                "- 使用工具读写文件、编辑代码、执行命令、搜索内容\n" +
                "- 通过 todo 工具管理任务列表，追踪复杂任务进度\n" +
                "- 加载专业技能以应对特定领域的任务\n\n" +
                "工作指南：\n" +
                "1. 优先使用工具解决任务，而非仅提供建议\n" +
                "2. 在处理复杂或不熟悉的主题时，使用 load_skill 工具获取专业知识\n" +
                "3. 使用 todo 工具将大任务分解为小步骤，逐个完成",
                root
        );
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 工具定义静态初始化依赖该实例，须先创建
        todoManager = new TodoManager();

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        // 基座工具（不含任何能力工具）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition, TodoDefinition));

        Agent06 agent = new Agent06(provider, MODEL, baseTools);

        // 子智能体能力：sub_agent 工具（独立上下文执行子任务并回传 token 用量）
        SubAgentCapability subagent = new SubAgentCapability();
        agent.capability(subagent);

        // 插拔式集成：复制 capability/compact/ 目录 + 这一行注册
        CompactCapability compact = new CompactCapability(provider, MODEL, sessionManager);
        agent.capability(compact);

        // 技能能力：load_skill 工具 + /skills 命令 + 技能描述提示片段
        SkillCapability skill = new SkillCapability();
        agent.capability(skill);

        // 基础提示 + 能力贡献的提示片段
        String prompt = generateSystemPrompt(ROOT);
        prompt += "\n\n" + String.join("\n", compact.promptFragments());
        prompt += "\n" + String.join("\n", skill.promptFragments());
        agent.setSystemPrompt(prompt
                + "\n" + String.join("\n", subagent.promptFragments()));

        System.out.println("=== 上下文压缩智能体 ===");
        System.out.println(compact.description());
        System.out.println();

        agent.setSessionManager(sessionManager);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
