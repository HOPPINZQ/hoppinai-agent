package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.Tools;
import com.hoppinzq.agent.tool.bus.MessageBus;
import com.hoppinzq.agent.tool.cron.CronScheduler;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;
import com.hoppinzq.agent.tool.task.TaskManager;
import com.hoppinzq.agent.tool.worktree.WorktreeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * Worktree 隔离示例（对应 Python 教程 s18_worktree_isolation）。
 *
 * <p>在 autonomous 的基础上，给每个 task 增加一个可选的 worktree 绑定。teammate 认领带 worktree 的
 * task 时，{@link com.hoppinzq.agent.tool.worktree.WorktreeContext} 会把当前线程的 cwd 切到
 * {@code .worktrees/<name>/}，此后该 teammate 的 bash / read_file / write_file / edit_file 都
 * 只在自己那份 worktree 里干活，不会和别的 teammate 撞车。
 *
 * <p>共 23 个工具：6 base + 3 cron + 3 teams + 3 protocol + 5 task + 3 worktree。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"创建 2 个 task（'修改 README'、'加 pom.xml'），分别 create_worktree 绑定，然后
 *        spawn alice 和 bob，观察他们在隔离目录里干活"</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentWorktree {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        CronScheduler cron = new CronScheduler();
        MessageBus bus = new MessageBus();
        ProtocolRegistry registry = new ProtocolRegistry();
        TaskManager taskManager = new TaskManager();
        WorktreeManager worktreeManager = new WorktreeManager(taskManager);

        Tools.setCronScheduler(cron);
        Tools.setLeadClient(client);
        Tools.setLeadModel(MODEL);
        Tools.setLeadBus(bus);
        Tools.setLeadRegistry(registry);
        Tools.setTaskManager(taskManager);
        Tools.setWorktreeManager(worktreeManager);

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

        // Lead 工具（23 个）
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
        // 3 个 worktree 工具
        tools.add(ToolDefinition.CreateWorktreeDefinition);
        tools.add(ToolDefinition.RemoveWorktreeDefinition);
        tools.add(ToolDefinition.KeepWorktreeDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.setCronScheduler(cron);
        agent.setMessageBus(bus);
        agent.setProtocolRegistry(registry);
        agent.setSessionManager(bootstrapSession(args));
        agent.run();
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java AgentWorktree <sessionId>} —— 直接恢复指定会话</li>
     *   <li>无参启动 —— 列出已有会话，输入序号恢复或回车开新会话</li>
     * </ul>
     */
    private static SessionManager bootstrapSession(String[] args) {
        SessionManager sm = new SessionManager();
        if (args.length > 0 && !args[0].isBlank()) {
            boolean ok = sm.resume(args[0]);
            System.out.println(ok
                    ? "已恢复会话 " + args[0]
                    : "会话 " + args[0] + " 不存在或为空，将以该 ID 开始新会话");
            return sm;
        }
        Scanner sc = new Scanner(System.in);
        List<String> sessions = sm.listSessions();
        if (sessions.isEmpty()) {
            sm.startNew();
            System.out.println("新会话已创建: " + sm.getSessionId());
            return sm;
        }
        System.out.println("\n\033[95m========== 历史会话 ==========\033[0m");
        for (int i = 0; i < sessions.size(); i++) {
            System.out.printf("\033[95m%d\033[0m) %s%n", i + 1, sessions.get(i));
        }
        System.out.print("输入序号恢复对应会话，或直接回车开启新会话: ");
        String line = sc.nextLine().trim();
        if (line.isEmpty()) {
            sm.startNew();
            System.out.println("新会话已创建: " + sm.getSessionId());
            return sm;
        }
        try {
            int idx = Integer.parseInt(line) - 1;
            if (idx >= 0 && idx < sessions.size()) {
                String id = sessions.get(idx);
                sm.resume(id);
                System.out.println("已恢复会话: " + id);
                return sm;
            }
        } catch (NumberFormatException ignore) {
            // 用户可能直接输入了 sessionId
            if (sessions.contains(line)) {
                sm.resume(line);
                System.out.println("已恢复会话: " + line);
                return sm;
            }
        }
        sm.startNew();
        System.out.println("输入无效，已开启新会话: " + sm.getSessionId());
        return sm;
    }

    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个带 worktree 隔离的 Lead。在 autonomous 的能力（teams + protocol + task + cron）之上，
                你还可以为每个 task 绑定一个 git worktree，spawn 出来的 teammate 认领 task 后会自动切到该 worktree
                目录干活，互不干扰。

                ## 关键工具
                - create_worktree(name, taskId?)：创建 wt/<name> 分支 + .worktrees/<name> 目录，可绑定到 task
                - remove_worktree(name, discard=true)：移除 worktree，discard=true 连分支一起删
                - keep_worktree(name)：标记保留，不自动清理

                ## 工作目录
                工作目录：%s
                操作系统：%s

                注意：worktree 必须在 git 仓库内运行（ROOT 的祖先目录里有 .git）；
                否则 create_worktree 会报错。teammate 认领带 worktree 的 task 后，
                该线程的 bash / read_file / write_file / edit_file 都会以 worktree 为 cwd。""", ROOT, osName);
    }
}
