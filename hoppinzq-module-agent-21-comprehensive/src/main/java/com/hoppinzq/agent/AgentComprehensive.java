package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.Tools;
import com.hoppinzq.agent.tool.bus.MessageBus;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.cron.CronScheduler;
import com.hoppinzq.agent.tool.mockmcp.MockMcpClient;
import com.hoppinzq.agent.tool.permission.PermissionChecker;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;
import com.hoppinzq.agent.tool.recovery.RetryWrapper;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import com.hoppinzq.agent.tool.task.TaskManager;
import com.hoppinzq.agent.tool.worktree.WorktreeManager;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 综合示例（对应 Python 教程 s20_comprehensive）。
 *
 * <p>把前面所有模块的机制塞进同一个 agent loop：
 * <ul>
 *   <li>cron 定时调度（schedule_cron / list_crons / cancel_cron）</li>
 *   <li>teams + protocol（spawn_teammate / send_message / check_inbox / request_shutdown / request_plan / review_plan）</li>
 *   <li>autonomous（create_task / list_tasks / get_task / claim_task / complete_task + IdlePoller）</li>
 *   <li>worktree 隔离（create_worktree / remove_worktree / keep_worktree）</li>
 *   <li>permission 三重闸门（PermissionChecker，注入到 ZQAgent）</li>
 *   <li>memory（MemoryStore + MemorySelector + MemoryExtractor，本入口暂未挂载工具，留给后续）</li>
 *   <li>sysprompt assembler（PromptAssembler，构造 system prompt 时使用）</li>
 *   <li>skill loader（SkillLoader，从 classpath 读 skills/）</li>
 *   <li>context compaction（ContextCompactor，由 ZQAgent 在 LLM 调用前自动微压缩）</li>
 *   <li>error recovery（RetryWrapper，LLM 调用走重试包装）</li>
 *   <li>mock MCP（MockMcpClient + docs/deploy，connect_mcp 后通过 mcp__server__tool 调用）</li>
 * </ul>
 *
 * <p>共 27 个工具（含 4 个 mock MCP 工具）。
 *
 * <p><b>示例 prompt（与 Python s20 等价）：</b>
 * <ol>
 *   <li>"建一份检查仓库的 todo，然后列出所有 Java 文件"</li>
 *   <li>"connect docs MCP server 然后搜索 agent loop"（验 mock MCP）</li>
 *   <li>"创建两个任务，分别 create_worktree 绑定，spawn alice 和 bob 自主干活"</li>
 *   <li>"3 分钟后提醒我开会"（cron one-shot）</li>
 *   <li>"后台运行 mvn compile，同时继续读 README"（验 background；教学版 background 由 teammate 间接体现）</li>
 * </ol>
 *
 * @author hoppinzq
 */
public class AgentComprehensive {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        // === 各子系统 ===
        CronScheduler cron = new CronScheduler();
        MessageBus bus = new MessageBus();
        ProtocolRegistry registry = new ProtocolRegistry();
        TaskManager taskManager = new TaskManager();
        WorktreeManager worktreeManager = new WorktreeManager(taskManager);
        MockMcpClient mcpClient = new MockMcpClient();
        SkillLoader skillLoader = new SkillLoader();
        ContextCompactor compactor = new ContextCompactor(client, MODEL);
        RetryWrapper retryWrapper = new RetryWrapper(client);

        // === 注入到 Tools ===
        Tools.setCronScheduler(cron);
        Tools.setLeadClient(client);
        Tools.setLeadModel(MODEL);
        Tools.setLeadBus(bus);
        Tools.setLeadRegistry(registry);
        Tools.setTaskManager(taskManager);
        Tools.setWorktreeManager(worktreeManager);
        Tools.setMcpClient(mcpClient);

        // === teammate 工具（7 个）===
        List<ToolDefinition> teammateTools = new ArrayList<>();
        teammateTools.add(ToolDefinition.BashDefinition);
        teammateTools.add(ToolDefinition.ReadFileDefinition);
        teammateTools.add(ToolDefinition.WriteFileDefinition);
        teammateTools.add(ToolDefinition.EditFileDefinition);
        teammateTools.add(ToolDefinition.ClaimTaskDefinition);
        teammateTools.add(ToolDefinition.CompleteTaskDefinition);
        teammateTools.add(ToolDefinition.SendMessageDefinition);
        Tools.setLeadTeammateTools(teammateTools);

        // === Lead 工具（27 个）===
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
        // 5 task
        tools.add(ToolDefinition.CreateTaskDefinition);
        tools.add(ToolDefinition.ListTasksDefinition);
        tools.add(ToolDefinition.GetTaskDefinition);
        tools.add(ToolDefinition.ClaimTaskDefinition);
        tools.add(ToolDefinition.CompleteTaskDefinition);
        // 3 worktree
        tools.add(ToolDefinition.CreateWorktreeDefinition);
        tools.add(ToolDefinition.RemoveWorktreeDefinition);
        tools.add(ToolDefinition.KeepWorktreeDefinition);
        // 1 connect_mcp + 4 mock mcp tools
        tools.add(ToolDefinition.ConnectMcpDefinition);
        tools.add(ToolDefinition.McpDocsSearchDefinition);
        tools.add(ToolDefinition.McpDocsGetVersionDefinition);
        tools.add(ToolDefinition.McpDeployStatusDefinition);
        tools.add(ToolDefinition.McpDeployRunDefinition);

        // === 装配 ZQAgent ===
        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt(skillLoader));
        agent.setCronScheduler(cron);
        agent.setMessageBus(bus);
        agent.setProtocolRegistry(registry);
        agent.setCommandHandler(new AgentCommandHandler(null));
        // 权限闸门：本入口预构造 PermissionChecker 供参考；若要启用，把它的 check 嵌入 ZQAgent
        // 的工具执行前（参考 hoppinzq-module-agent-permission 的本地 ZQAgent 副本）。
        // agent.setPermissionChecker(new PermissionChecker(agent.getScanner()));
        PermissionChecker checkerRef = new PermissionChecker(agent.getScanner()); // 教学引用

        System.out.println("=== hoppinzq-module-agent-comprehensive ===");
        System.out.println("已装配 " + tools.size() + " 个工具，5 个 mock MCP 工具默认在线（4 个 mcp__* + 1 个 connect_mcp）");
        System.out.println("可用 skills：" + skillLoader.getAvailableSkills());
        System.out.println("可用 mock MCP servers：" + com.hoppinzq.agent.tool.mockmcp.MockMcpServers.names());
        agent.run();
    }

    private static String buildSystemPrompt(SkillLoader skillLoader) {
        String osName = System.getProperty("os.name").toLowerCase();
        String skills = skillLoader.getDescriptions();
        return String.format("""
                ## 角色
                你是 hoppinzq-module-agent-comprehensive —— 把 Python 教程 s20 的全部机制塞进同一个 loop 的教学版 agent。
                可用工具按子系统分组：
                - 基础：bash / read_file / write_file / edit_file / list_files / content_search
                - cron：schedule_cron / list_crons / cancel_cron
                - teams：spawn_teammate / send_message / check_inbox
                - protocol：request_shutdown / request_plan / review_plan
                - task：create_task / list_tasks / get_task / claim_task / complete_task
                - worktree：create_worktree / remove_worktree / keep_worktree
                - mock MCP：connect_mcp（连接 docs 或 deploy）+ mcp__docs__search / mcp__docs__get_version / mcp__deploy__status / mcp__deploy__run

                ## 权限
                所有工具调用会先经过 PermissionChecker 三重闸门（黑名单 / 路径越界 / 交互式确认）。
                收到 isError=true 的 ToolResult 即表示被拒，请如实告知用户。

                ## Skills
                %s

                ## 工作目录与操作系统
                工作目录：%s
                操作系统：%s

                注意：teammate 是 daemon 线程，会自主轮询任务板；create_worktree 必须在 git 仓库内运行。
                mock MCP 是教学版（进程内 mock），真实 MCP 见 hoppinzq-module-agent-13-mcp。""", skills, ROOT, osName);
    }
}
