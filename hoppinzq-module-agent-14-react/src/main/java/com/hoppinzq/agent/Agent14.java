package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.react.ReActCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * ReAct Agent 示例（对应 Python 教程 s13_react）。
 *
 * <p>本类只负责<b>装配</b>：ReAct 的格式约束（Thought / Action / Action Input）
 * 收拢在 {@link ReActCapability}（capability.react 包），通过 {@code promptFragments()}
 * 注入系统提示；工具调用仍走内核的标准 tool_calls 通道，主循环无需任何改动。
 *
 * <p>插拔式集成：复制 {@code capability/react/} 目录 + 一行 {@code agent.capability(...)}。
 *
 * @author hoppinzq
 */
public class Agent14 extends ZQAgent {

    /** 命令处理器需要技能加载器（/skills），工具定义静态初始化也依赖它，须先赋值 */
    public static SkillLoader skillLoader;

    public Agent14(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();
        skillLoader = new SkillLoader();

        SessionManager sessionManager = SessionManager.bootstrap(args);

        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition));

        Agent14 agent = new Agent14(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/react/ 目录 + 这一行注册
        // ReAct 提示需要工具清单，故构造时传入 tools
        ReActCapability react = new ReActCapability(baseTools);
        agent.capability(react);

        // 提示词完全由能力贡献（ReAct 或普通模式，取决于 ReactConfig.REACT_ENABLE）
        agent.setSystemPrompt(String.join("\n\n", react.promptFragments()));

        System.out.println("=== ReAct 智能体 ===");
        System.out.println(react.description());
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        agent.run();
    }
}
