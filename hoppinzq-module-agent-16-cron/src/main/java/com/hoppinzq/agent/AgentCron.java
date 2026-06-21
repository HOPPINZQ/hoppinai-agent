package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.Tools;
import com.hoppinzq.agent.tool.cron.CronScheduler;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 定时调度示例（对应 Python 教程 s14_cron_scheduler）。
 *
 * <p>在 module-02 工具循环的基础上，新增两条 daemon 线程：
 * <ol>
 *   <li><b>调度线程</b>：每秒轮询当前时间，命中 5 字段 cron 表达式（min hour dom mon dow）的任务
 *       入 {@code cronQueue}；同一分钟内不重复触发（lastFire 防抖）</li>
 *   <li><b>队列处理线程</b>：保留为扩展位，主循环通过 {@code consumeQueue()} 在 LLM 调用前主动消费</li>
 * </ol>
 *
 * <p>durable=true 的任务持久化到 {@code <ROOT>/.scheduled_tasks.json}，进程重启后自动恢复。
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>"每 2 分钟打印当前日期"（用 schedule_cron，cron 表达式星号斜杠 2 空格星号空格星号空格星号空格星号）</li>
 *   <li>"1 分钟后提醒我检查构建状态"（one-shot：recurring=false）</li>
 *   <li>"列出所有 cron 任务"</li>
 *   <li>"取消任务 &lt;id&gt;"</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentCron {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        // 6 个原有工具 + 3 个 cron 工具
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

        // 启动 cron 调度器
        CronScheduler scheduler = new CronScheduler();
        Tools.setCronScheduler(scheduler);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.setCronScheduler(scheduler);
        agent.run();
    }

    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个带定时任务调度能力的开发助手。除了常规的 bash / 文件工具，你还拥有三个 cron 工具：
                - schedule_cron(cron, prompt, recurring=true, durable=false)：注册一个 5 字段 cron 任务，命中时把 prompt 作为新 user 消息注入对话
                - list_crons()：列出所有已注册的 cron 任务
                - cancel_cron(id)：按 id 取消一个 cron 任务

                cron 表达式字段顺序：minute hour day-of-month month day-of-week。
                - 每 N 分钟：*/N * * * *
                - 每天 HH:MM：M H * * *
                - 每周 X 的 H 点：0 H * * D（0=周日，1=周一…6=周六）

                ## 工作目录
                %s

                ## 操作系统
                %s

                注意：定时任务命中的 prompt 会作为 system reminder 注入对话；如果用户让你「N 分钟后做 X」，
                请用 schedule_cron 配 recurring=false 实现 one-shot 提醒。""", ROOT, osName);
    }
}
