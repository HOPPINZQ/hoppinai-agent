package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.background.BackgroundCapability;
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
 * 后台任务智能体。
 *
 * <p>本类只负责<b>装配</b>，两个能力各自插拔：
 * <ul>
 *   <li>{@link TaskCapability}（capability.task 包）—— 任务图持久化与 task_* 工具</li>
 *   <li>{@link BackgroundCapability}（capability.background 包）—— 后台线程池、
 *       background_run / check_background 工具、结果通知注入（beforeLlmCall 钩子）、
 *       线程池生命周期（onStart/onStop）</li>
 * </ul>
 * 原先入口类持有的静态 manager、onToolExecution 覆写、main 里的
 * {@code finally { shutdown() }} 全部随能力化删除。
 *
 * @author hoppinzq
 */
public class Agent08 extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值（基座工具能力化是后续工作，本模式暂保留）。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    public Agent08(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；后台任务与任务系统的用法由各自能力的
     * promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root, String skillDescriptions) {
        return String.format("""
                你是一个专业的编程智能体，工作在目录: %s

                ## 核心能力

                ## 文件操作
                - read_file: 读取文件内容
                - write_file: 写入文件内容
                - edit_file: 编辑文件（替换字符串）
                - glob: 查找文件

                ## 代码搜索
                - content_search: 使用ripgrep搜索代码内容，支持正则表达式

                ## 命令执行
                - bash: 执行Shell命令（适用于快速命令）

                ## 任务管理
                - task_create / task_update / task_list / task_get: 管理任务与依赖（由 task 能力提供）
                - todo: 管理待办事项列表

                ## 高级功能
                - sub_agent: 委托子智能体处理复杂任务
                - load_skill: 加载特定技能

                # 工作指南

                1. **优先使用后台任务**: 耗时命令不阻塞对话，完成后结果自动注入
                2. **合理使用任务管理**: 将大任务分解为小任务，便于跟踪进度
                3. **充分利用搜索**: 使用 content_search 快速定位代码
                4. **对于复杂任务**: 使用 sub_agent 委托处理

                # 技能系统

                ## 可用技能

                %s

                ## 技能使用策略

                1. **主动学习**：遇到不熟悉的领域时，主动使用 load_skill 工具获取专业知识
                2. **何时加载技能**：遇到不熟悉的编程概念、需要遵循特定最佳实践、执行标准化工作流程时
                3. **技能加载流程**：识别知识缺口 → 使用 load_skill 加载 → 阅读理解 → 应用知识解决问题
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

        Agent08 agent = new Agent08(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/<feature>/ 目录 + 各一行注册
        TaskCapability task = new TaskCapability(sessionManager);
        BackgroundCapability background = new BackgroundCapability();
        agent.capability(task);
        agent.capability(background);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT, skillLoader.getDescriptions())
                + "\n\n" + String.join("\n", task.promptFragments())
                + "\n\n" + String.join("\n", background.promptFragments()));

        System.out.println("=== 后台任务智能体 ===");
        System.out.println(background.description());
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 能力生命周期：Agent 关闭时回收后台线程池
            background.onStop();
        }
    }
}
