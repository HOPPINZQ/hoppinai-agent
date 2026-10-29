package com.hoppinzq.agent.capability.recovery;

/**
 * 错误恢复能力的自持配置。
 * <p>特性常量不放在内核 {@code AIConstants} 里——能力被拔除时这些常量应随之消失，
 * 内核保持纯净（参见 capability-rollout-guide 第 3 节）。
 *
 * @author hoppinzq
 */
public final class RecoveryConfig {

    private RecoveryConfig() {
    }

    /**
     * 529（overloaded）连续命中达到阈值后切换的兜底模型；null 表示不切换，
     * 只做指数退避（DeepSeek 单模型场景下没有等价兜底）。
     */
    public static final String FALLBACK_MODEL = null;
    /** 指数退避基础延迟（毫秒）：实际延迟 = min(BASE × 2^attempt, MAX) + jitter */
    public static final long BASE_DELAY_MS = 500L;
    /** 单次退避延迟上限（毫秒） */
    public static final long MAX_DELAY_MS = 32_000L;
    /** reactive compact 保留的最近消息条数 */
    public static final int KEEP_RECENT = 6;
}
