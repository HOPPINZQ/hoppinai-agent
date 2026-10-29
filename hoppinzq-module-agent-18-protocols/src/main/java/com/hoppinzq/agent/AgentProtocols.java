package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.cron.CronCapability;
import com.hoppinzq.agent.capability.protocol.ProtocolCapability;
import com.hoppinzq.agent.capability.teams.MessageBus;
import com.hoppinzq.agent.capability.teams.TeamsCapability;
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
 * 团队协作 + 协议状态机示例（对应 Python 教程 s16_team_protocols）。
 *
 * <p>本类只负责<b>装配</b>：三个能力包拼装出完整形态 ——
 * <ul>
 *   <li>{@link CronCapability}（capability.cron）：定时任务</li>
 *   <li>{@link TeamsCapability}（capability.teams）：spawn_teammate / send_message / check_inbox + 邮箱轮询</li>
 *   <li>{@link ProtocolCapability}（capability.protocol）：request_shutdown / request_plan / review_plan
 *       + 协议回执路由（回执以 user 消息注入下一轮上下文）</li>
 * </ul>
 *
 * <p>插拔式集成：复制三个 capability 目录 + 三行 {@code agent.capability(...)}。
 * 邮箱是无状态文件实现，各能力各自持有 MessageBus 实例也能读写同一批邮箱文件。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"Spawn alice 让她做事，然后 request_shutdown alice"</li>
 *   <li>"Spawn bob 做重构，让他先提交 plan，然后你 review approve"</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentProtocols extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    public AgentProtocols(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    /**
     * 基础系统提示词（不含能力片段；cron / teams / protocol 的用法说明由各自 promptFragments 贡献）。
     */
    public static String generateSystemPrompt(String root) {
        return String.format("""
                ## 角色
                你是一个 lead agent（团队负责人），可以派 teammate 去并行干活，
                并通过协议（request_shutdown / request_plan / review_plan）管理 teammate 的生命周期与方案审批。

                ## 工作指南
                1. 优先使用工具解决任务，而非仅提供建议
                2. 复杂任务先 spawn_teammate 分批派发，自己继续做别的事
                3. 每轮主循环前会自动轮询你的邮箱，teammate 的回执与协议消息会出现在下一轮对话里

                ## 工作目录
                %s""", root);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        SessionManager sessionManager = SessionManager.bootstrap(args);

        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition));

        AgentProtocols agent = new AgentProtocols(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability 目录 + 三行注册
        CronCapability cron = new CronCapability();
        agent.capability(cron);

        List<ToolDefinition> teammateTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, WriteFileDefinition));
        TeamsCapability teams = new TeamsCapability(provider, MODEL, teammateTools);
        agent.capability(teams);

        ProtocolCapability protocol = new ProtocolCapability(new MessageBus());
        agent.capability(protocol);

        agent.setSystemPrompt(generateSystemPrompt(ROOT) + "\n\n"
                + String.join("\n", cron.promptFragments()) + "\n"
                + String.join("\n", teams.promptFragments()) + "\n"
                + String.join("\n", protocol.promptFragments()));

        System.out.println("=== 团队协议智能体 ===");
        System.out.println(cron.description());
        System.out.println(teams.description());
        System.out.println(protocol.description());
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
