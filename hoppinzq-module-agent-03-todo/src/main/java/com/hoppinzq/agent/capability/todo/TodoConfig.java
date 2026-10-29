package com.hoppinzq.agent.capability.todo;

/**
 * 待办能力自持配置。
 * <p>属于 todo 能力的私有常量，参照 {@code capability/compact/CompactConfig} 的模式，
 * 不进内核 {@code AIConstants}——这样拔除 capability/todo 目录即可完全移除该能力，
 * 不会在内核常量类里留下孤儿配置。
 *
 * @author hoppinzq
 */
public final class TodoConfig {
    private TodoConfig() {
    }

    // 待办列表长度上限（超过即拒绝更新，防止模型无限制堆任务）
    public static final int MAX_TODOS = 20;
    // 连续多少个回合没有更新待办列表，就向上下文注入一次提醒
    public static final int REMINDER_ROUNDS = 3;
}
