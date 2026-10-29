package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.autonomous.AutonomousCapability;
import com.hoppinzq.agent.capability.cron.CronCapability;
import com.hoppinzq.agent.capability.protocol.ProtocolCapability;
import com.hoppinzq.agent.capability.teams.MessageBus;
import com.hoppinzq.agent.capability.task.TaskCapability;
import com.hoppinzq.agent.capability.teams.TeamsCapability;
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
 * 自主 Agent 示例（对应 Python 教程 s17_autonomous_agents）。
 *
 * <p>在 protocols 的基础上引入"任务板"：teammate 通过 IdlePoller 轮询未认领任务并自动认领，
 * 进入 WORK → IDLE 两阶段自主循环。
 *
 * <p>本类只负责<b>装配</b>：cron / teams / protocol 三个能力包从 capability 目录注册
 * （复制目录 + 一行注册即可复用），任务板工具由本模块的 ToolDefinition 提供。
 *
 * <p>插拔式集成：每个能力一行 {@code agent.capability(...)}。
 *
 * @author hoppinzq
 */
public class AgentAutonomous extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    public AgentAutonomous(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /** 基础系统提示词（能力片段由各能力贡献）。 */
    public static String generateSystemPrompt(String root) {
        return String.format("""
                ## 角色
                你是一个 lead agent（团队负责人）。团队采用任务板 + 自主认领协作：
                teammate 在空闲时会轮询任务板，发现未认领任务就自动认领并开始工作。

                ## 工作指南
                1. 用 create_task 把工作拆成任务板上的条目，用 spawn_teammate 拉起 teammate
                2. 用 claim_task / complete_task 追踪任务状态；teammate 会自动认领未分配任务
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

        AgentAutonomous agent = new AgentAutonomous(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability 目录 + 三行注册
        CronCapability cron = new CronCapability();
        agent.capability(cron);

        // 任务板能力（create_task / claim_task / ...）
        TaskCapability task = new TaskCapability();
        agent.capability(task);

        // 自主认领能力：实现 teams 的 TeammateIdleHook，teammate 空闲时自动认领任务板上的活
        AutonomousCapability autonomous = new AutonomousCapability(task.getTaskManager());
        agent.capability(autonomous);

        // teammate 的工具子集：基础文件工具 + 任务板工具（认领/完成），
        // 任务板工具实例直接取自 TaskCapability，保证与 lead 侧是同一个 TaskManager
        List<ToolDefinition> teammateTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, WriteFileDefinition));
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

        System.out.println("=== 自主智能体 ===");
        System.out.println(cron.description());
        System.out.println(teams.description());
        System.out.println(protocol.description());
        System.out.println(task.description());
        System.out.println(autonomous.description());
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