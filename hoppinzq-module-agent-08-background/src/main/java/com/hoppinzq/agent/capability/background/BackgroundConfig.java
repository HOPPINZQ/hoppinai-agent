package com.hoppinzq.agent.capability.background;

/**
 * background 能力自持的配置常量。
 * <p>属于 background 能力的特性常量，不进内核 {@code AIConstants}，
 * 保证能力目录可整体复制拔除。
 *
 * @author hoppinzq
 */
public final class BackgroundConfig {
    private BackgroundConfig() {
    }

    // 单个后台任务的执行超时（秒），超时后强制结束进程并标记 timeout
    public static final int RUN_TIMEOUT_SECONDS = 300;

    // 任务结果持久化在任务对象中的最大字符数（防止大输出撑爆内存）
    public static final int MAX_RESULT_CHARS = 50_000;

    // 通知队列中单条通知摘要的最大字符数（完整结果经 check_background 查询）
    public static final int MAX_NOTIFICATION_CHARS = 500;

    // 命令在通知/任务列表中的摘要长度
    public static final int COMMAND_SUMMARY_CHARS = 80;

    // 保留的已完成任务上限（超出后按开始时间淘汰最旧的）
    public static final int MAX_RETAINED_TASKS = 100;
}
