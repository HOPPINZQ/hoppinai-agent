package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.Tools;
import com.hoppinzq.agent.tool.bus.MessageBus;
import com.hoppinzq.agent.tool.cron.CronScheduler;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 团队协作 + 协议状态机示例（对应 Python 教程 s16_team_protocols）。
 *
 * <p>在 cron 副本基础上新增：
 * <ul>
 *   <li>teams 包：{@link MessageBus} + TeammateRunner + TeammateSpawner</li>
 *   <li>protocol 包：{@link com.hoppinzq.agent.tool.protocol.ProtocolRegistry} + ProtocolDispatcher + ProtocolState</li>
 *   <li>6 个新工具：3 teams（spawn_teammate / send_message / check_inbox）+ 3 protocol
 *       （request_shutdown / request_plan / review_plan）</li>
 * </ul>
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"Spawn alice 让她做事，然后 request_shutdown alice"</li>
 *   <li>"Spawn bob 做重构，让他先提交 plan，然后你 review approve"</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentProtocols {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        MessageBus bus = new MessageBus();
        CronScheduler cron = new CronScheduler();
        Tools.setCronScheduler(cron);
        Tools.setLeadClient(client);
        Tools.setLeadModel(MODEL);
        Tools.setLeadBus(bus);

        // teammate 自己可用的工具：bash / read / write / send_message
        List<ToolDefinition> teammateTools = new ArrayList<>();
        teammateTools.add(ToolDefinition.BashDefinition);
        teammateTools.add(ToolDefinition.ReadFileDefinition);
        teammateTools.add(ToolDefinition.WriteFileDefinition);
        teammateTools.add(ToolDefinition.SendMessageDefinition);
        Tools.setLeadTeammateTools(teammateTools);

        // lead 的完整工具集：6 base + 3 cron + 3 teams + 3 protocol = 15
        List<ToolDefinition> tools = new ArrayList<>();
        // 6 base
        tools.add(ToolDefinition.BashDefinition);
        tools.add(ToolDefinition.ReadFileDefinition);
        tools.add(ToolDefinition.WriteFileDefinition);
        tools.add(ToolDefinition.EditFileDefinition);
        tools.add(ToolDefinition.ListFilesDefinition);
        tools.add(ToolDefinition.ContentSearchDefinition);
        // 3 cron
        tools.add(ToolDefinition.ScheduleCronDefinition);
        tools.add(ToolDefinition.ListCronsDefinition);
        tools.add(ToolDefinition.CancelCronDefinition);
        // 3 teams
        tools.add(ToolDefinition.SpawnTeammateDefinition);
        tools.add(ToolDefinition.SendMessageDefinition);
        tools.add(ToolDefinition.CheckInboxDefinition);
        // 3 protocol
        tools.add(ToolDefinition.RequestShutdownDefinition);
        tools.add(ToolDefinition.RequestPlanDefinition);
        tools.add(ToolDefinition.ReviewPlanDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.setCronScheduler(cron);
        agent.setMessageBus(bus);
        // 让 Tools 里的 protocol 工具能访问 agent 的 registry
        Tools.setLeadRegistry(agent.getProtocolRegistry());

        agent.run();
    }

    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是团队的 Lead（主控 agent），可以 spawn 多个 teammate 线程并行做事，并通过协议状态机管理它们：
                - shutdown 协议：lead 发 shutdown_request → teammate 回 shutdown_response 并退出
                - plan_approval 协议：teammate 发 plan_approval_request → lead 用 review_plan 决定 approve/reject → teammate 收到 plan_approval_response

                ## 你拥有的工具（15 个）
                基础（6）：bash、read_file、write_file、edit_file、list_files、content_search
                定时（3）：schedule_cron、list_crons、cancel_cron
                团队（3）：
                - spawn_teammate(name, role, prompt)：启动一个 teammate 线程
                - send_message(to, content, type=message)：向 lead 或 teammate 发消息
                - check_inbox()：读取并清空自己的 mailbox
                协议（3）：
                - request_shutdown(teammate)：请求某 teammate 关闭
                - request_plan(teammate, plan?)：让 teammate 提交方案
                - review_plan(requestId, approved, comment?)：审批 teammate 提交的方案

                ## 工作目录
                %s

                ## 操作系统
                %s

                注意：teammate 通过 mailbox 异步通信，你需要主动 check_inbox 或在每轮结束后自动 poll；
                lead 主循环每轮工具循环结束后会自动 poll 自己的 inbox，把协议消息回灌为 system reminder。""",
                ROOT, osName);
    }
}
