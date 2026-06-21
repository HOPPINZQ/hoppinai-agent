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
 * agent teams 示例（对应 Python 教程 s15_agent_teams）。
 *
 * <p>在 cron 模块的基础上（保留 cron 工具链，保持自包含）新增 3 个团队协作工具：
 * <ol>
 *   <li><b>spawn_teammate(name, role, prompt)</b>：启动一个 teammate 守护线程，
 *       它有自己的对话上下文与工具子集（bash / read_file / write_file / send_message），
 *       与 lead 通过 {@code <ROOT>/.mailboxes/*.jsonl} 异步通信。</li>
 *   <li><b>send_message(to, content)</b>：往某人的邮箱追加一条消息。
 *       lead 调用时 from=lead；teammate 在自己线程里调用时 from=其名字（靠 ThreadLocal 区分）。</li>
 *   <li><b>check_inbox()</b>：读取并清空 lead 自己的邮箱，返回未读消息。</li>
 * </ol>
 *
 * <p>同时 ZQAgent 每轮主循环结束后会自动 pollInbox，把 teammate 发来的消息注入下一轮对话。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"Spawn alice 作为后端开发，让她创建 schema.sql"</li>
 *   <li>"检查 inbox 看 alice 的结果"</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentTeams {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        MessageBus bus = new MessageBus(); // 无状态，仅作为依赖占位

        // lead 工具：6 个基础 + 3 个 cron + 3 个 team = 12
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ToolDefinition.BashDefinition);
        tools.add(ToolDefinition.ReadFileDefinition);
        tools.add(ToolDefinition.WriteFileDefinition);
        tools.add(ToolDefinition.EditFileDefinition);
        tools.add(ToolDefinition.ListFilesDefinition);
        tools.add(ToolDefinition.ContentSearchDefinition);
        tools.add(ToolDefinition.ScheduleCronDefinition);
        tools.add(ToolDefinition.ListCronsDefinition);
        tools.add(ToolDefinition.CancelCronDefinition);
        tools.add(ToolDefinition.SpawnTeammateDefinition);
        tools.add(ToolDefinition.SendMessageDefinition);
        tools.add(ToolDefinition.CheckInboxDefinition);

        // teammate 工具子集：bash / read_file / write_file / send_message
        List<ToolDefinition> teammateTools = new ArrayList<>();
        teammateTools.add(ToolDefinition.BashDefinition);
        teammateTools.add(ToolDefinition.ReadFileDefinition);
        teammateTools.add(ToolDefinition.WriteFileDefinition);
        teammateTools.add(ToolDefinition.SendMessageDefinition);

        Tools.setLeadClient(client);
        Tools.setLeadModel(MODEL);
        Tools.setLeadBus(bus);
        Tools.setLeadTeammateTools(teammateTools);

        // teams 模块仍然保留 cron，保持自包含链式派生
        CronScheduler scheduler = new CronScheduler();
        Tools.setCronScheduler(scheduler);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.setMessageBus(bus);
        agent.setTeammateClient(client);
        agent.setTeammateTools(teammateTools);
        agent.setCronScheduler(scheduler);
        agent.run();
    }

    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个 lead agent（团队负责人），可以派 teammate 去并行干活。你拥有 12 个工具：
                - 基础：bash / read_file / write_file / edit_file / list_files / content_search
                - 定时：schedule_cron / list_crons / cancel_cron
                - 团队：spawn_teammate / send_message / check_inbox

                ## 团队工具用法
                - spawn_teammate(name, role, prompt)：启动一个 teammate，它在独立的守护线程里跑自己的 agent 循环，
                  拥有 bash/read_file/write_file/send_message 工具。spawn 立即返回，不要等待。
                - 每个 teammate 用完最多 10 轮 LLM 调用就会自动结束，并通过文件邮箱给你回执。
                - check_inbox()：读取你的邮箱（<ROOT>/.mailboxes/lead.jsonl）。每轮主循环结束我会自动 pollInbox，
                  所以即使你不主动 check_inbox，teammate 的消息也会出现在下一轮对话里。
                - send_message(to, content)：主动给某个 teammate 发消息。

                ## 邮箱机制
                每个角色（lead 或 teammate）在 <ROOT>/.mailboxes/ 下有一个 {name}.jsonl 文件作为收件箱。
                读即消费：readInbox 一次读完全部然后清空文件，不会重复消费。

                ## 工作目录
                %s

                ## 操作系统
                %s

                典型流程：spawn_teammate 派任务 → 继续做别的事 → 下一轮自动收到 teammate 的回执。
                """, ROOT, osName);
    }
}
