package com.hoppinzq.agent.tool.cron;

import java.time.Instant;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cron 调度器：两条 daemon 线程。
 * <ul>
 *   <li><b>调度线程</b>：每秒轮询当前时间，命中 cron 表达式（且距上次触发 ≥ 60s）则把 prompt 入 {@link #cronQueue}</li>
 *   <li><b>队列处理线程</b>：每 200ms 检查队列非空 + agent 空闲，则把队头 prompt 出队</li>
 * </ul>
 * agent 主循环每轮 LLM 调用前调用 {@link #consumeQueue()} 取已就绪的 prompt。
 *
 * @author hoppinzq
 */
public class CronScheduler {

    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();
    /** 命中后待消费的 prompt 队列（agent 主循环从这里取） */
    private final ConcurrentLinkedQueue<String> cronQueue = new ConcurrentLinkedQueue<>();
    /** agent 主循环空闲标志：跑完一轮工具循环且等下一次 user input 时置 true */
    private final AtomicBoolean agentIdle = new AtomicBoolean(true);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "cron-scheduler-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    private volatile boolean started = false;

    public void start() {
        if (started) return;
        started = true;
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::dispatch, 0, 200, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    /** agent 是否处于空闲（无活动 LLM 调用 / 工具执行）。 */
    public void markBusy() { agentIdle.set(false); }
    public void markIdle() { agentIdle.set(true); }

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
            if (jobs.containsKey(j.getId())) continue;
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
                if (!expr.matches(now)) continue;
                // 防同分钟重复触发：上次触发距今 < 60s 跳过
                if (job.getLastFire() != null && (now - job.getLastFire()) < 60_000L) continue;
                cronQueue.add(job.getPrompt());
                job.setLastFire(now);
                long nf = expr.nextFireAfter(now);
                job.setNextFire(nf);
                System.out.printf("\u001b[95m[cron]\u001b[0m 命中 %s (%s) → 入队%n", job.getId(), job.getCron());
                if (!job.isRecurring()) {
                    jobs.remove(job.getId());
                }
                CronStore.save(new ArrayList<>(jobs.values()));
            } catch (Exception e) {
                System.err.println("[cron] 任务 " + job.getId() + " 扫描异常：" + e.getMessage());
            }
        }
    }

    /** 队列处理线程：把 prompt 从队头出队，等 agent 空闲时再交还主循环。 */
    private void dispatch() {
        // 仅做最小工作：实际消费交给主循环 consumeQueue；
        // 这里负责把队头延迟到 agent idle 时才出队，避免工具调用过程中插入用户消息打乱 state
        // 由于 consumeQueue 已有 idle 检查，此处留作扩展位（教学版可空实现）
    }

    /**
     * 主循环每轮 LLM 调用前调用，返回当前应该注入到 history 的 prompt；空则不注入。
     */
    public String consumeQueue() {
        if (!agentIdle.get()) return null;
        String p = cronQueue.poll();
        if (p == null) return null;
        System.out.printf("\u001b[95m[cron]\u001b[0m 注入定时提示：%s%n", p);
        return p;
    }

    /** 检查当前是否还有任何待消费 prompt（用于 README 提到的诊断） */
    public int queueSize() {
        return cronQueue.size();
    }

    public boolean isIdle() { return agentIdle.get(); }

    public Instant now() { return Instant.now(); }
}
