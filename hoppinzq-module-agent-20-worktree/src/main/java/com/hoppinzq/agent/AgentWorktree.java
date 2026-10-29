package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.autonomous.AutonomousCapability;
import com.hoppinzq.agent.capability.cron.CronCapability;
import com.hoppinzq.agent.capability.protocol.ProtocolCapability;
import com.hoppinzq.agent.capability.task.TaskCapability;
import com.hoppinzq.agent.capability.teams.MessageBus;
import com.hoppinzq.agent.capability.teams.TeamsCapability;
import com.hoppinzq.agent.capability.worktree.WorktreeCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * Worktree 隔离示例（对应 Python 教程 s18_worktree_isolation）。
 *
 * <p>在 autonomous 的基础上引入 git worktree 隔离：teammate 处理 task 时，
 * WorktreeContext 会把当前线程的 cwd 切到独立的 worktree 目录，互不干扰。
 *
 * <p>本类只负责<b>装配</b>：cron / teams / protocol 三个能力包从 capability 目录注册，
 * 任务板与 worktree 工具由本模块的 ToolDefinition 提供。
 *
 * @author hoppinzq
 */
public class AgentWorktree extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    public AgentWorktree(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /** 基础系统提示词（能力片段由各能力贡献）。 */
    public static String generateSystemPrompt(String root) {
        return String.format("""
                ## 角色
                你是一个 lead agent（团队负责人）。每个 teammate 在独立 git worktree 中工作，
                彼此的代码改动互不干扰。

                ## 工作指南
                1. 先用 create_worktree 为任务开隔离工作区，再 spawn_teammate 派活
                2. 用 task 系列工具追踪进度；完成后 keep_worktree 或 remove_worktree 收尾
                3. 每轮主循环前会自动轮询你的邮箱，teammate 的回执与协议消息会出现在下一轮对话里

                ## 工作目录
                %s""", root);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        SessionManager sessionManager = SessionManager.bootstrap(args);

        List<ToolDefinition> baseTools = new ArrayList<>(Arrays.asList(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                ListFilesDefinition, ContentSearchDefinition));

        AgentWorktree agent = new AgentWorktree(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability 目录 + 三行注册
        CronCapability cron = new CronCapability();
        agent.capability(cron);

        // 任务板 + 自主认领：teammate 空闲时自动认领任务
        TaskCapability task = new TaskCapability();
        agent.capability(task);

        AutonomousCapability autonomous = new AutonomousCapability(task.getTaskManager());
        agent.capability(autonomous);

        // worktree 隔离：create/keep/remove 三个工具，可绑定任务
        WorktreeCapability worktree = new WorktreeCapability(task.getTaskManager());
        agent.capability(worktree);

        // teammate 工具子集：文件工具 + 任务板认领/完成
        List<ToolDefinition> teammateTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition));
        for (ToolDefinition t : task.tools()) {
            if ("claim_task".equals(t.getName()) || "complete_task".equals(t.getName())
                    || "list_tasks".equals(t.getName()) || "get_task".equals(t.getName())) {
                teammateTools.add(t);
            }
        }
        TeamsCapability teams = new TeamsCapability(provider, MODEL, teammateTools)
                .idleHook(autonomous);
        agent.capability(teams);

        ProtocolCapability protocol = new ProtocolCapability(new MessageBus());
        agent.capability(protocol);

        String prompt = generateSystemPrompt(ROOT);
        prompt += "\n\n" + String.join("\n", cron.promptFragments());
        prompt += "\n" + String.join("\n", teams.promptFragments());
        prompt += "\n" + String.join("\n", protocol.promptFragments());
        agent.setSystemPrompt(prompt);

        System.out.println("=== Worktree 隔离智能体 ===");
        System.out.println(cron.description());
        System.out.println(teams.description());
        System.out.println(protocol.description());
        System.out.println(task.description());
        System.out.println(autonomous.description());
        System.out.println(worktree.description());
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            teams.onStop();
            cron.onStop();
        }
    }
}