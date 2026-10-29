package com.hoppinzq.agent.capability.compact;

/**
 * 压缩配置 - 四层压缩策略（参考Python s08设计）。
 * <p>属于 compact 能力自持的配置，不进内核 {@code AIConstants}。
 *
 * @author hoppinzq
 */
public final class CompactConfig {
    private CompactConfig() {
    }

    // L1 snip_compact: 最大消息数
    public static final int MAX_MESSAGES = 50;
    // L1 snip_compact: 保留头部消息数
    public static final int KEEP_HEAD = 3;
    // L2 micro_compact: 保留最近的工具结果数
    public static final int KEEP_RECENT = 3;
    // L3 tool_result_budget: 持久化阈值（字节）
    public static final int PERSIST_THRESHOLD = 30000;
    // L3 tool_result_budget: 单次消息最大字节数
    public static final int MAX_BYTES_PER_MESSAGE = 200_000;
    // L4 auto_compact: token阈值
    public static final int CONTEXT_LIMIT = 10000;
}
