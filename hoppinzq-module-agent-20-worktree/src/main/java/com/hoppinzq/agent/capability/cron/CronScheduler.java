package com.hoppinzq.agent.capability.cron;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.hoppinzq.agent.capability.cron.CronConfig.SAME_FIRE_DEBOUNCE_MS;
import static com.hoppinzq.agent.capability.cron.CronConfig.TICK_INTERVAL_SECONDS;
import static com.hoppinzq.agent.capability.cron.CronConfig.DISPATCH_INTERVAL_MS;

/**
 * Cron 调度器：两条 daemon 线程。
 * <ul>
 *   <li><b>调度线程</b>（每 {@link CronConfig#TICK_INTERVAL_SECONDS} 秒）：轮询当前时间，
 *       命中 5 字段 cron 表达式（且距上次触发 ≥ 防抖窗口）的任务把 prompt 入 {@link #cronQueue}</li>
 *   <li><b>分发线程</b>（每 {@link CronConfig#DISPATCH_INTERVAL_MS} 毫秒）：把队头 prompt 通过
 *       {@link #promptRunner} 回调投递出去；能力包在 onStart 时注入回调，最终由宿主 Agent 跑任务</li>
 * </ul>
 * <p>与旧版（内核主循环每轮 consumeQueue）的差异：内核主循环阻塞在标准输入上无法被外部注入，
 * 因此"消费队列"改为回调投递，由 {@code CronCapability} 决定宿主何时空闲、如何执行。
 *
 * @author hoppinzq
 */
public class CronScheduler {

    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();
    /** 命中后待投递的 prompt 队列（宿主忙碌时由能力侧 requeue 攒回来） */
    private final ConcurrentLinkedQueue<String> cronQueue = new ConcurrentLinkedQueue<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "cron-scheduler-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    private volatile boolean started = false;

    /** 命中后的投递回调；为空时 prompt 只在队列里积压，不会丢失 */
    private volatile Consumer<String> promptRunner;

    /** 由 CronCapability 在 onStart 注入：命中 prompt 的真正执行方式 */
    public void setPromptRunner(Consumer<String> promptRunner) {
        this.promptRunner = promptRunner;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_SECONDS, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::dispatch, DISPATCH_INTERVAL_MS, DISPATCH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public String scheduleJob(CronJob spec) {
        CronJob job = spec;
        if (job.getId() == null) {
            job.setId(UUID.randomUUID().toString().substring(0, 8));
        }
        // 试解析，错误立即抛
        CronExpression expr = new CronExpression(job.getCron());
        job.setNextFire(expr.nextFireAfter(System.currentTimeMillis()));
        jobs.put(job.getId(), job);
        CronStore.save(new ArrayList<>(jobs.values()));
        return job.getId();
    }

    public boolean cancelJob(String id) {
        CronJob removed = jobs.remove(id);
        if (removed != null) {
            CronStore.save(new ArrayList<>(jobs.values()));
            return true;
        }
        return false;
    }

    public List<CronJob> listJobs() {
        // 保留插入顺序便于展示
        Map<String, CronJob> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, CronJob> e : jobs.entrySet()) {
            ordered.put(e.getKey(), e.getValue());
        }
        return new ArrayList<>(ordered.values());
    }

    /** 启动时把 durable 任务塞回内存。 */
    public void restoreDurable() {
        List<CronJob> persisted = CronStore.load();
        for (CronJob j : persisted) {
            if (jobs.containsKey(j.getId())) {
                continue;
            }
            // 重启后下一次命中仍要触发；把 lastFire 置空避免被「同分钟」逻辑误判跳过
            j.setLastFire(null);
            try {
                CronExpression expr = new CronExpression(j.getCron());
                j.setNextFire(expr.nextFireAfter(System.currentTimeMillis()));
            } catch (Exception ignore) {
                // 表达式坏掉的任务跳过
                continue;
            }
            jobs.put(j.getId(), j);
        }
    }

    /** 调度线程：扫描命中 cron 的任务入队。 */
    private void tick() {
        long now = System.currentTimeMillis();
        for (CronJob job : jobs.values()) {
            try {
                CronExpression expr = new CronExpression(job.getCron());
                if (!expr.matches(now)) {
                    continue;
                }
                // 防同分钟重复触发：上次触发距今不足防抖窗口则跳过
                if (job.getLastFire() != null && (now - job.getLastFire()) < SAME_FIRE_DEBOUNCE_MS) {
                    continue;
                }
                cronQueue.add(job.getPrompt());
                job.setLastFire(now);
                long nf = expr.nextFireAfter(now);
                job.setNextFire(nf);
                System.out.printf("\u001b[95m[cron]\u001b[0m 命中 %s (%s) -> 入队%n", job.getId(), job.getCron());
                if (!job.isRecurring()) {
                    jobs.remove(job.getId());
                }
                CronStore.save(new ArrayList<>(jobs.values()));
            } catch (Exception e) {
                System.err.println("[cron] 任务 " + job.getId() + " 扫描异常：" + e.getMessage());
            }
        }
    }

    /** 分发线程：把队头 prompt 交给投递回调（宿主忙碌时由回调负责 requeue）。 */
    private void dispatch() {
        Consumer<String> runner = promptRunner;
        if (runner == null) {
            return;
        }
        String prompt = cronQueue.poll();
        if (prompt != null) {
            runner.accept(prompt);
        }
    }

    /** 把 prompt 放回队头队列（宿主忙碌时稍后再投） */
    public void requeue(String prompt) {
        cronQueue.add(prompt);
    }

    /** 当前待投递的 prompt 数（诊断用） */
    public int queueSize() {
        return cronQueue.size();
    }
}
