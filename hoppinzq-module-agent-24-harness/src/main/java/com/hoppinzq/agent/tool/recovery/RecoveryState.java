package com.hoppinzq.agent.tool.recovery;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 重试与恢复过程的共享状态。
 * <p>
 * 每个会话共享一份，用于在 {@link RetryWrapper} 多次重试间传递信号：
 * <ul>
 *   <li>{@link #attempt} — 当前连续重试次数</li>
 *   <li>{@link #consecutive529} — 连续命中 overload 的次数，达到阈值切换 fallback 模型</li>
 *   <li>{@link #maxTokens} — 输出截断时自动升级（8K → 64K）</li>
 *   <li>{@link #model} / {@link #fallbackModel} — 当前生效模型 + 529 后的兜底</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
public class RecoveryState {

    /** 初始 / 默认上限 */
    public static final int INITIAL_MAX_TOKENS = 8_000;
    /** 升级后上限 */
    public static final int UPGRADED_MAX_TOKENS = 64_000;
    /** 连续 529 多少次后切换 fallback model */
    public static final int OVERLOAD_SWITCH_THRESHOLD = 3;
    /** 最大重试次数 */
    public static final int MAX_ATTEMPTS = 6;

    private final AtomicInteger attempt = new AtomicInteger(0);
    private final AtomicInteger consecutive529 = new AtomicInteger(0);

    private int maxTokens = INITIAL_MAX_TOKENS;
    private String model;
    private String fallbackModel;
    private boolean switchedToFallback = false;

    public RecoveryState(String model, String fallbackModel) {
        this.model = model;
        this.fallbackModel = fallbackModel;
    }

    /** 调用成功时清零 */
    public void reset() {
        attempt.set(0);
        consecutive529.set(0);
    }

    /** 输出截断时升级 maxTokens；返回是否还有升级空间 */
    public boolean upgradeMaxTokens() {
        if (maxTokens >= UPGRADED_MAX_TOKENS) {
            return false;
        }
        maxTokens = UPGRADED_MAX_TOKENS;
        return true;
    }

    /** 连续 529 次数累加；达到阈值切到 fallback */
    public boolean increment529AndCheckSwitch() {
        int n = consecutive529.incrementAndGet();
        if (n >= OVERLOAD_SWITCH_THRESHOLD && fallbackModel != null && !switchedToFallback) {
            switchedToFallback = true;
            return true;
        }
        return false;
    }

    public int incrementAttempt() {
        return attempt.incrementAndGet();
    }
}
