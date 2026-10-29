package com.hoppinzq.agent.capability.memory;

/**
 * 记忆能力的自持配置。
 * <p>特性常量不放在内核 {@code AIConstants} 里——能力被拔除时这些常量应随之消失，
 * 内核保持纯净（参见 capability-rollout-guide 第 3 节）。
 *
 * @author hoppinzq
 */
public final class MemoryConfig {

    private MemoryConfig() {
    }

    /** 记忆明细目录（相对工作目录 ROOT） */
    public static final String MEMORY_DIR = ".memory";
    /** 记忆索引文件（每条记忆一行摘要） */
    public static final String MEMORY_INDEX_FILE = "MEMORY.md";
    /** 每轮最多注入的记忆条数，避免上下文膨胀 */
    public static final int MAX_INJECT = 5;
    /** 记忆文件数阈值，达到后提示 consolidate（合并 / 摘要） */
    public static final int CONSOLIDATE_THRESHOLD = 10;
    /** 是否在每轮对话结束后抽取新记忆（关闭后只注入不落盘） */
    public static final boolean EXTRACT_ENABLED = true;
    /** 注入系统提示的单条记忆正文最大长度（超长截断，防止个别记忆撑爆提示） */
    public static final int MEMORY_BODY_MAX_CHARS = 400;
}
