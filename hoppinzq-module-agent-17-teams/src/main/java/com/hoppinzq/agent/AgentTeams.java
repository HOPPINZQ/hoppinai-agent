package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.cron.CronCapability;
import com.hoppinzq.agent.capability.teams.TeamsCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * agent teams 示例（对应 Python 教程 s15_agent_teams）。
 *
 * <p>本类只负责<b>装配</b>：团队协作（spawn_teammate / send_message / check_inbox）与邮箱轮询
 * 收拢在 {@link TeamsCapability}（capability.teams 包），定时任务链收拢在
 * {@link CronCapability}（capability.cron 包）。Agent 每轮不再覆写主循环。
 *
 * <p>插拔式集成：复制 {@code capability/teams/} 与 {@code capability/cron/} 目录
 * + 两行 {@code agent.capability(...)}。
 *
 * <p>每个 teammate 是一个守护线程，有自己的对话上下文与工具子集，与 lead 之间
 * 通过 {@code <ROOT>/.mailboxes/*.jsonl} 文件邮箱异步通信。
 *
 * @author hoppinzq
 */
public class AgentTeams extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值。
     */
    public static SkillLoader skillLoader;
    /** 工具定义静态初始化依赖，须在构建工具列表之前赋值 */
    public static com.hoppinzq.agent.tool.manager.TodoManager todoManager;

    public AgentTeams(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；团队/定时用法由各能力的 promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root) {
        return String.format("""
                ## 角色
                你是一个 lead agent（团队负责人），可以派 teammate 去并行干活。

                ## 工作指南
                1. 优先使用工具解决任务，而非仅提供建议
                2. 复杂任务先 spawn_teammate 分批派发，自己继续做别的事
                3. 每轮主循环结束会自动轮询你的邮箱，teammate 的回执会出现在下一轮对话里

                ## 工作目录
                %s""", root);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();
        skillLoader = new SkillLoader();
        todoManager = new com.hoppinzq.agent.tool.manager.TodoManager();

        SessionManager sessionManager = SessionManager.bootstrap(args);

        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition));

        AgentTeams agent = new AgentTeams(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability 目录 + 两行注册
        CronCapability cron = new CronCapability();
        agent.capability(cron);

        // teammate 工具子集：bash / read_file / write_file / send_message
        // send_message 由 TeamsCapability 自身注册，这里只需要基座工具
        List<ToolDefinition> teammateTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, WriteFileDefinition));
        TeamsCapability teams = new TeamsCapability(provider, MODEL, teammateTools);
        agent.capability(teams);

        agent.setSystemPrompt(generateSystemPrompt(ROOT) + "\n\n"
                + String.join("\n", cron.promptFragments()) + "\n"
                + String.join("\n", teams.promptFragments()));

        System.out.println("=== 团队协作智能体 ===");
        System.out.println(teams.description());
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
