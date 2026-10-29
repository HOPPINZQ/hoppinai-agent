package com.hoppinzq.agent.capability.cron;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.hoppinzq.agent.capability.cron.CronConfig.BUSY_STALE_MS;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * cron 定时调度能力（可整体复制的能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>3 个工具：{@code schedule_cron} / {@code list_crons} / {@code cancel_cron}</li>
 *   <li>1 个命令：{@code /crons}（查看已注册任务与队列积压）</li>
 *   <li>生命周期：{@link #onStart} 启动调度器（daemon 线程），{@link #onStop} 关闭</li>
 *   <li>系统提示片段：告知模型存在定时调度能力</li>
 * </ul>
 *
 * <p>调度命中后的执行方式：内核主循环阻塞在标准输入上，无法从外部"注入"一条用户消息，
 * 因此命中时把 prompt 交给 {@code onStart} 捕获的宿主 Agent，直接以
 * {@link ZQAgent#runTask(String)} 跑完一轮工具循环——prompt 仍进入共享上下文，
 * 模型在后续对话里能看到定时任务的执行痕迹。
 *
 * <p>对内核的依赖仅限：Capability 契约、AIConstants.ROOT（持久化目录）。
 * 拔除本目录并删除注册行即可完全移除该能力。
 *
 * @author hoppinzq
 */
public class CronCapability implements Capability {

    private final CronScheduler scheduler = new CronScheduler();

    /** onStart 捕获的宿主 Agent：调度命中后由它执行任务 prompt */
    private volatile ZQAgent agent;

    /** 宿主是否正处于一轮 LLM 调用中（beforeLlmCall 置忙 / afterLlmResponse 置闲） */
    private final AtomicBoolean hostInFlight = new AtomicBoolean(false);

    /** 宿主最近一次 LLM 活动时间戳；配合 {@link #hostInFlight} 做忙标记过期自愈 */
    private final AtomicLong lastLlmActivity = new AtomicLong(0);

    /** cron 任务执行线程池：单线程串行，保证同一时刻最多跑一个定时任务 */
    private volatile ExecutorService cronWorker;

    @Override
    public String name() {
        return "cron";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
        // 命中投递回调：调度线程只负责"发现"，真正的执行交给单线程 worker 在宿主 agent 上跑
        scheduler.setPromptRunner(this::dispatchPrompt);
        scheduler.restoreDurable();
        scheduler.start();
        cronWorker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cron-worker");
            t.setDaemon(true);
            return t;
        });
        System.out.println("[能力 cron] 调度器已启动（durable 任务已恢复 " + scheduler.listJobs().size() + " 个）");
    }

    @Override
    public void onStop() {
        scheduler.shutdown();
        ExecutorService w = cronWorker;
        if (w != null) {
            w.shutdownNow();
        }
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        ToolDefinition scheduleCron = new ToolDefinition(
                "schedule_cron",
                "调度一个定时任务。命中 5 字段 cron 表达式（min hour day-of-month month day-of-week）时，把 prompt 作为任务交给 agent 执行。\n" +
                        "示例：\n" +
                        "- 每 2 分钟跑一次：*/2 * * * *\n" +
                        "- 每天 9:30：30 9 * * *\n" +
                        "- 每周一 9 点：0 9 * * 1\n" +
                        "recurring=false 表示只触发一次（one-shot）；durable=true 表示进程重启后仍然有效。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "cron", ToolDefinition.createProperty("string", "5 字段 cron 表达式，例如 '*/2 * * * *'."),
                                "prompt", ToolDefinition.createProperty("string", "触发时要执行的任务 prompt。"),
                                "recurring", ToolDefinition.createProperty("boolean", "是否周期触发，默认 true。"),
                                "durable", ToolDefinition.createProperty("boolean", "是否持久化到 " + CronConfig.STORE_FILE + "，默认 false。")
                        ),
                        List.of("cron", "prompt")
                ),
                CronScheduleInput.class,
                this::invokeScheduleCron);

        ToolDefinition listCrons = new ToolDefinition(
                "list_crons",
                "列出当前所有已注册的 cron 任务（id、cron、prompt、recurring、durable、lastFire、nextFire）。无需参数。",
                ToolDefinition.createInputSchema(Map.of(), List.of()),
                Map.class,
                this::invokeListCrons);

        ToolDefinition cancelCron = new ToolDefinition(
                "cancel_cron",
                "按 id 取消一个已注册的 cron 任务。",
                ToolDefinition.createInputSchema(
                        Map.of("id", ToolDefinition.createProperty("string", "要取消的任务 id（来自 list_crons）")),
                        List.of("id")
                ),
                CronCancelInput.class,
                this::invokeCancelCron);

        return List.of(scheduleCron, listCrons, cancelCron);
    }

    /** schedule_cron 执行体：注册一个 cron 任务（原 Tools 静态方法收编为能力私有方法，直接调用调度器） */
    private String invokeScheduleCron(CronScheduleInput in) {
        if (in.getCron() == null || in.getCron().isBlank()) {
            return "错误：cron 表达式不能为空";
        }
        if (in.getPrompt() == null || in.getPrompt().isBlank()) {
            return "错误：prompt 不能为空";
        }
        // 先验证表达式可解析，坏表达式立即报错而不是等调度线程扫到再炸
        try {
            new CronExpression(in.getCron());
        } catch (IllegalArgumentException e) {
            return "错误：cron 表达式无效 — " + e.getMessage();
        }
        try {
            CronJob job = CronJob.builder()
                    .cron(in.getCron())
                    .prompt(in.getPrompt())
                    .recurring(in.isRecurring())
                    .durable(in.isDurable())
                    .build();
            String id = scheduler.scheduleJob(job);
            return "已注册 cron 任务 id=" + id + " cron='" + in.getCron() + "' recurring=" + in.isRecurring()
                    + " durable=" + in.isDurable();
        } catch (Exception e) {
            return "schedule_cron 错误: " + e.getMessage();
        }
    }

    /**
     * list_crons 执行体：列出所有任务。
     * <p>入参声明为 {@code Map}（工具无参数）以匹配 ToolDefinition 的
     * {@code (Class<T>, TypedToolFunction<T>)} 重载，避免与 Function 重载产生二义性。
     */
    private String invokeListCrons(Map input) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(scheduler.listJobs());
        } catch (Exception e) {
            return "list_crons 错误: " + e.getMessage();
        }
    }

    /** cancel_cron 执行体：按 id 取消任务 */
    private String invokeCancelCron(CronCancelInput in) {
        if (in.getId() == null || in.getId().isBlank()) {
            return "错误：id 不能为空";
        }
        boolean ok = scheduler.cancelJob(in.getId());
        return ok ? "已取消 cron 任务 " + in.getId() : "未找到 cron 任务 " + in.getId();
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
                // 记录宿主 LLM 活动：调度线程据此判断"宿主正在跑一轮"，避免并发改写上下文
                hostInFlight.set(true);
                lastLlmActivity.set(System.currentTimeMillis());
                return messages;
            }

            @Override
            public void afterLlmResponse(com.hoppinzq.agent.client.LlmResponse response) {
                hostInFlight.set(false);
                lastLlmActivity.set(System.currentTimeMillis());
            }
        });
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/crons";
            }

            @Override
            public String description() {
                return "查看已注册的 cron 任务与待投递队列";
            }

            @Override
            public void execute() {
                printCrons();
            }
        });
    }

    /** /crons：打印任务表与队列积压（诊断用） */
    private void printCrons() {
        List<CronJob> jobs = scheduler.listJobs();
        if (jobs.isEmpty()) {
            System.out.println("[cron] 当前没有已注册的定时任务");
        } else {
            System.out.println("========== cron 任务 ==========");
            for (CronJob job : jobs) {
                System.out.printf("  [%s] cron='%s' recurring=%s durable=%s%n      prompt: %s%n",
                        job.getId(), job.getCron(), job.isRecurring(), job.isDurable(), job.getPrompt());
            }
        }
        System.out.println("[cron] 待投递队列积压: " + scheduler.queueSize());
    }

    // ============================== 调度命中执行 ==============================

    /**
     * 调度线程命中后的投递入口：宿主空闲时交给单线程 worker 在宿主 agent 上跑任务。
     * <p>宿主忙碌（正在一轮 LLM 调用）时把 prompt 放回队列稍后再试，
     * 避免与主循环并发改写 messageParams。
     */
    private void dispatchPrompt(String prompt) {
        if (hostBusy()) {
            scheduler.requeue(prompt);
            return;
        }
        ExecutorService w = cronWorker;
        if (w == null) {
            scheduler.requeue(prompt);
            return;
        }
        w.submit(() -> {
            ZQAgent host = agent;
            if (host == null) {
                return;
            }
            System.out.println("[cron] 触发定时任务: " + prompt);
            try {
                host.runTask(prompt);
            } catch (Exception e) {
                System.err.println("[cron] 任务执行失败: " + e.getMessage());
            }
        });
    }

    /**
     * 宿主是否忙碌：inFlight 且活动时间未过期。
     * 若 LLM 调用抛异常导致"置闲"钩子没执行，忙标记会在 BUSY_STALE_MS 后自动过期（自愈）。
     */
    private boolean hostBusy() {
        return hostInFlight.get()
                && System.currentTimeMillis() - lastLlmActivity.get() < BUSY_STALE_MS;
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "## 定时任务（cron）\n" +
                "你可以用 schedule_cron / list_crons / cancel_cron 管理定时任务：\n" +
                "- schedule_cron(cron, prompt, recurring=true, durable=false)：注册一个 5 字段 cron 任务，" +
                "命中时把 prompt 作为任务自动交给 agent 执行\n" +
                "- cron 表达式字段顺序：minute hour day-of-month month day-of-week\n" +
                "  - 每 N 分钟：*/N * * * *\n" +
                "  - 每天 HH:MM：M H * * *\n" +
                "  - 每周 X 的 H 点：0 H * * D（0=周日，1=周一…6=周六）\n" +
                "- recurring=false 实现 one-shot 提醒；durable=true 的任务进程重启后仍会恢复\n" +
                "- 如果用户让你「N 分钟后做 X」，请用 schedule_cron 配 recurring=false 实现");
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "cron 调度：扫描周期 " + CronConfig.TICK_INTERVAL_SECONDS + "s，投递周期 "
                + CronConfig.DISPATCH_INTERVAL_MS + "ms，防抖窗口 " + (CronConfig.SAME_FIRE_DEBOUNCE_MS / 1000)
                + "s，durable 持久化文件 " + CronConfig.STORE_FILE;
    }
}
