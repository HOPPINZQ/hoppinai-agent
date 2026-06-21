package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.Tools;
import com.hoppinzq.agent.tool.bus.MessageBus;
import com.hoppinzq.agent.tool.cron.CronScheduler;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;
import com.hoppinzq.agent.tool.task.TaskManager;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 自主 Agent 示例（对应 Python 教程 s17_autonomous_agents）。
 *
 * <p>在 protocols 的基础上，引入"任务板"：teammate 通过 IdlePoller 轮询未认领任务并自动认领，
 * 在 WORK → IDLE 两阶段循环中自主干活，完成后自动 complete_task。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"创建 3 个任务（'创建 README', '创建 pom.xml', '列出所有文件'），然后 spawn alice 和 bob，
 *        看他们自动认领并干活"</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentAutonomous {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        CronScheduler cron = new CronScheduler();
        MessageBus bus = new MessageBus();
        ProtocolRegistry registry = new ProtocolRegistry();
        TaskManager taskManager = new TaskManager();

        Tools.setCronScheduler(cron);
        Tools.setLeadClient(client);
        Tools.setLeadModel(MODEL);
        Tools.setLeadBus(bus);
        Tools.setLeadRegistry(registry);
        Tools.setTaskManager(taskManager);

        // Teammate 工具（7 个）：bash、文件读写编辑、claim/complete task、send_message
        List<ToolDefinition> teammateTools = new ArrayList<>();
        teammateTools.add(ToolDefinition.BashDefinition);
        teammateTools.add(ToolDefinition.ReadFileDefinition);
        teammateTools.add(ToolDefinition.WriteFileDefinition);
        teammateTools.add(ToolDefinition.EditFileDefinition);
        teammateTools.add(ToolDefinition.ClaimTaskDefinition);
        teammateTools.add(ToolDefinition.CompleteTaskDefinition);
        teammateTools.add(ToolDefinition.SendMessageDefinition);
        Tools.setLeadTeammateTools(teammateTools);

        // Lead 工具（20 个）
        List<ToolDefinition> tools = new ArrayList<>();
        // 6 个基础工具
        tools.add(ToolDefinition.BashDefinition);
        tools.add(ToolDefinition.ReadFileDefinition);
        tools.add(ToolDefinition.WriteFileDefinition);
        tools.add(ToolDefinition.EditFileDefinition);
        tools.add(ToolDefinition.ListFilesDefinition);
        tools.add(ToolDefinition.ContentSearchDefinition);
        // 3 个 cron 工具
        tools.add(ToolDefinition.ScheduleCronDefinition);
        tools.add(ToolDefinition.ListCronsDefinition);
        tools.add(ToolDefinition.CancelCronDefinition);
        // 3 个 team 工具
        tools.add(ToolDefinition.SpawnTeammateDefinition);
        tools.add(ToolDefinition.SendMessageDefinition);
        tools.add(ToolDefinition.CheckInboxDefinition);
        // 3 个 protocol 工具
        tools.add(ToolDefinition.RequestShutdownDefinition);
        tools.add(ToolDefinition.RequestPlanDefinition);
        tools.add(ToolDefinition.ReviewPlanDefinition);
        // 5 个 task 工具
        tools.add(ToolDefinition.CreateTaskDefinition);
        tools.add(ToolDefinition.ListTasksDefinition);
        tools.add(ToolDefinition.GetTaskDefinition);
        tools.add(ToolDefinition.ClaimTaskDefinition);
        tools.add(ToolDefinition.CompleteTaskDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.setCronScheduler(cron);
        agent.setMessageBus(bus);
        agent.setProtocolRegistry(registry);
        agent.run();
    }

    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个带自主 teammate 的开发助手（Lead）。除常规文件/命令/cron 工具外，你还有：
                - **spawn_teammate**（spawn 一个自主 agent into its own thread）
                - **send_message / check_inbox**（基于文件的消息总线）
                - **request_shutdown / request_plan / review_plan**（shutdown/审批协议）
                - **create_task / list_tasks / get_task / claim_task / complete_task**（任务板）

                Spawn 的 teammate 会自动轮询任务板（IdlePoller），
                发现有可认领任务后通过 AutoClaimer 认领，进入 WORK → IDLE 两阶段自主循环，
                完成后自动 complete_task 并发消息告知你结果。
                你可以创建任务后 spawn 若干个 teammate，观察他们并行干活。

                ## 工作目录与环江
                工作目录：%s
                操作系统：%s

                注意：teammate 是 daemon 线程，会并行执行；如果需要控制顺序，用 blockedBy 依赖。""", ROOT, osName);
    }
}
