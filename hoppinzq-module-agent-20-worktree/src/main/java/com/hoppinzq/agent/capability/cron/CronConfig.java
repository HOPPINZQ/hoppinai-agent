package com.hoppinzq.agent.capability.cron;

/**
 * cron 能力的自持配置（替代原 AIConstants 中的特性常量；06 权威源 CompactConfig 同款做法）。
 * <p>本类收拢 cron 特性的全部可调参数：调度/分发节奏、防抖窗口、持久化文件名，
 * 以及"宿主是否空闲"的判定阈值。内核 AIConstants 不再包含任何 cron 相关常量。
 *
 * @author hoppinzq
 */
public final class CronConfig {

    private CronConfig() {
    }

    /** 调度线程扫描周期（秒）：每秒对齐一次当前时间，命中 cron 表达式即入队 */
    public static final long TICK_INTERVAL_SECONDS = 1L;

    /** 队列分发周期（毫秒）：dispatch 线程每隔该时长尝试把队头 prompt 投递给宿主 agent */
    public static final long DISPATCH_INTERVAL_MS = 200L;

    /** 防同分钟重复触发窗口（毫秒）：距上次触发不足 60s 的命中直接跳过 */
    public static final long SAME_FIRE_DEBOUNCE_MS = 60_000L;

    /** durable 任务的持久化文件（相对 ROOT；能力自持存储，参照 TranscriptStore） */
    public static final String STORE_FILE = ".scheduled_tasks.json";

    /**
     * 宿主"正在跑一轮对话"的最长认定时长（毫秒）。
     * <p>能力无法从内核拿到"主循环正在忙"的确切信号，只能用钩子近似：
     * beforeLlmCall 置忙、afterLlmResponse 置闲；若 LLM 调用抛异常导致置闲钩子没跑，
     * 忙标记会在该时长后自动过期（漏标记自愈），避免定时任务被永久饿死。
     * 取值须大于 provider 的最长超时（AIConstants.TIMEOUT = 5 分钟）。
     */
    public static final long BUSY_STALE_MS = 330_000L;
}
