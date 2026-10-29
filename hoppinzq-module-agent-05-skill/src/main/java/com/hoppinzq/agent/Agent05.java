package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.skill.SkillCapability;
import com.hoppinzq.agent.capability.subagent.SubAgentCapability;
import com.hoppinzq.agent.capability.todo.TodoCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 技能加载智能体。
 *
 * <p>本类只负责<b>装配</b>：load_skill 工具与技能提示片段收拢在 {@link SkillCapability}
 * （capability.skill 包）；sub_agent 工具收拢在 {@link SubAgentCapability}；
 * todo 工具、提醒钩子与 /todos 命令收拢在 {@link TodoCapability}。
 * 入口类不再持有 {@code SkillLoader}/{@code TodoManager} 静态实例，也不再覆写任何循环方法。
 *
 * <p>这就是插拔式集成的完整形态：复制 capability/&lt;feature&gt;/ 目录 +
 * 下面 main 中的三行 {@code agent.capability(new XxxCapability(...))}。
 * 技能文件结构：
 * <pre>
 * skills/
 *   pdf/
 *     SKILL.md  # ---\n name: pdf\n description: Process PDF files\n ---\n ...
 *   code-review/
 *     SKILL.md  # ---\n name: code-review\n description: Review code\n ---\n ...
 * </pre>
 *
 * @author hoppinzq
 */
public class Agent05 extends ZQAgent {

    public Agent05(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 基座工具（不含任何能力工具；load_skill / sub_agent / todo 由能力注册）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition));

        Agent05 agent = new Agent05(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/<feature>/ 目录 + 这三行注册
        TodoCapability todo = new TodoCapability();
        SubAgentCapability subAgent = new SubAgentCapability();
        SkillCapability skill = new SkillCapability();
        agent.capability(todo);
        agent.capability(subAgent);
        agent.capability(skill);

        // 基础提示 + 各能力贡献的提示片段（技能片段即 Layer 1：只放名称与描述）
        agent.setSystemPrompt(buildSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", todo.promptFragments())
                + "\n\n" + String.join("\n", subAgent.promptFragments())
                + "\n\n" + String.join("\n", skill.promptFragments()));

        System.out.println("=== 技能加载智能体 ===");
        System.out.println(todo.description());
        System.out.println(subAgent.description());
        System.out.println(skill.description());
        System.out.println();

        // 能力自持的 SkillLoader 注入内核命令处理器：内置 /skills 命令的数据来源由能力供给
        agent.setSessionManager(SessionManager.bootstrap(args), skill.getLoader());
        agent.run();
    }

    /**
     * 基础系统提示词（不含能力片段；todo / 子智能体 / 技能的专属文案由各自能力的
     * promptFragments 贡献）。
     *
     * @return 格式化的系统提示词
     */
    private static String buildSystemPrompt(String root) {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
            你是一个编程智能体，负责拆解并完成用户交给你的任务。
            你工作在 %s 操作系统，工作根目录为：%s

            ## 核心原则

            1. **委托处理**：复杂或重复性任务可以委托给子智能体处理
            2. **工具优先**：优先使用工具解决问题，而不是仅提供建议
            3. **安全第一**：执行命令前考虑安全性，避免破坏性操作

            ## 工作流程

            1. **理解需求**：仔细分析用户需求，明确任务目标
            2. **任务规划**：使用 todo 工具创建任务计划
            3. **执行实施**：使用合适的工具完成任务
            4. **验证反馈**：检查结果，向用户提供反馈

            ## 工具使用指南

            ### bash 工具
            - 用于执行系统命令
            - 支持 cmd、powershell、bash 三种类型
            - 执行前确认命令安全性

            ### 文件操作工具
            - read_file: 读取文件内容
            - write_file: 写入新文件
            - edit_file: 编辑现有文件
            - glob: 列出目录内容
            - content_search: 搜索文件内容

            记住：你的目标是帮助用户解决问题，而不仅仅是提供建议。使用你的工具来实际执行任务。
            """, osName, root);
    }
}
