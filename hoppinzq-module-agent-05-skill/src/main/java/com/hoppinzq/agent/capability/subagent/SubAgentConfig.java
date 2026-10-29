package com.hoppinzq.agent.capability.subagent;

/**
 * 子智能体能力自持配置。
 * <p>属于 subagent 能力的私有常量（参照 {@code capability/compact/CompactConfig} 的模式），
 * 不进内核 {@code AIConstants}——拔除 capability/subagent 目录时配置随之消失，
 * 内核常量类不会留下孤儿项。
 *
 * @author hoppinzq
 */
public final class SubAgentConfig {
    private SubAgentConfig() {
    }

    // 子智能体单次任务的最大循环次数（预留阈值；当前内核 runTask 循环未接入该上限，
    // 保留常量是为了后续给内核挂载点接入时语义不变）
    public static final int MAX_ITERATIONS = 30;
}
