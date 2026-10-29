package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.compact.CompactCapability;
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
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    public Agent06(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；能力通过 promptFragments 贡献自己的部分）。
     */
    public static String generateSystemPrompt(String root, String skillDescriptions) {
        return String.format(
                "你是一个编程智能体，工作在目录: %s\n\n" +
                "核心能力：\n" +
                "- 使用工具读写文件、编辑代码、执行命令、搜索内容\n" +
                "- 通过 todo 工具管理任务列表，追踪复杂任务进度\n" +
                "- 加载专业技能以应对特定领域的任务\n\n" +
                "工作指南：\n" +
                "1. 优先使用工具解决任务，而非仅提供建议\n" +
                "2. 在处理复杂或不熟悉的主题时，使用 load_skill 工具获取专业知识\n" +
                "3. 使用 todo 工具将大任务分解为小步骤，逐个完成\n\n" +
                "可用的技能：\n%s",
                root,
                skillDescriptions
        );
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 工具定义静态初始化依赖这两个实例，须先创建
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        // 基座工具（不含任何能力工具）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition, SubAgentDefinition, TodoDefinition,
                SkillsDefinition));

        Agent06 agent = new Agent06(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/compact/ 目录 + 这一行注册
        CompactCapability compact = new CompactCapability(provider, MODEL, sessionManager);
        agent.capability(compact);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT, skillLoader.getDescriptions())
                + "\n\n" + String.join("\n", compact.promptFragments()));

        System.out.println("=== 上下文压缩智能体 ===");
        System.out.println(compact.description());
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
