package com.hoppinzq.agent.capability.autonomous;

/**
 * autonomous 能力自持的配置常量。
 *
 * @author hoppinzq
 */
public final class AutonomousConfig {
    private AutonomousConfig() {
    }

    /** 空闲轮询间隔（毫秒）：连续无工作时的睡眠时长 */
    public static final long POLL_INTERVAL_MS = 5_000L;

    /** 空闲轮询总上限（毫秒）：超过即判定无事可做，teammate 退出 */
    public static final long POLL_TIMEOUT_MS = 60_000L;
}