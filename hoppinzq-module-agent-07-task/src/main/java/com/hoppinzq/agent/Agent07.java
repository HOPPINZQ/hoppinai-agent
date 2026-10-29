package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.task.TaskCapability;
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
 * 任务系统智能体。
 *
 * <p>本类只负责<b>装配</b>：任务图（持久化、依赖、状态流转）、任务工具、/tasks 命令、
 * 任务提示词片段全部收拢在 {@link TaskCapability}（capability.task 包），
 * 入口类不再持有任何静态 manager，也不再覆写主循环方法。
 *
 * <p>这就是插拔式集成的完整形态：复制 {@code capability/task/} 目录 +
 * 下面 main 中的一行 {@code agent.capability(new TaskCapability(...))}。
 *
 * @author hoppinzq
 */
public class Agent07 extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值（基座工具能力化是后续工作，本模式暂保留）。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    public Agent07(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；任务系统的用法由 TaskCapability.promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root, String skillDescriptions) {
        return String.format("""
                你是一个具有任务管理能力的编程智能体，工作在目录: %s

                ## 核心能力

                - **文件操作**：读写、编辑、搜索文件内容
                - **命令执行**：运行Shell命令和脚本
                - **任务管理**：创建、更新、查询和管理任务（由 task 能力提供）
                - **待办跟踪**：维护和更新待办事项列表
                - **技能扩展**：加载专业技能以应对特定领域任务

                ## 工作原则

                1. **任务优先**：接收复杂任务时，首先使用 task_create 创建任务列表
                2. **状态同步**：任务开始时标记为 in_progress，完成后标记为 completed
                3. **优先使用工具解决任务**，而非仅提供建议
                4. **主动更新**：定期检查并更新待办事项，反映当前工作状态

                ## 工具使用规范

                - **bash**：执行Shell命令，优先使用专用工具（glob、content_search 等）
                - **read_file**：读取文件内容，支持多种文件格式
                - **edit_file**：编辑现有文件，精确字符串替换
                - **write_file**：创建新文件或完全重写现有文件
                - **glob**：使用 Glob 模式查找文件
                - **content_search**：使用 ripgrep 搜索文件内容

                ## 可用技能

                %s
                """, root, skillDescriptions);
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

        Agent07 agent = new Agent07(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/task/ 目录 + 这一行注册
        TaskCapability task = new TaskCapability(sessionManager);
        agent.capability(task);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT, skillLoader.getDescriptions())
                + "\n\n" + String.join("\n", task.promptFragments()));

        System.out.println("=== 任务系统智能体 ===");
        System.out.println(task.description());
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
