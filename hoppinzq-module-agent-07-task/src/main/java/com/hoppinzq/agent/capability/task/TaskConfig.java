package com.hoppinzq.agent.capability.task;

/**
 * task 能力自持的配置常量。
 * <p>属于 task 能力的特性常量，不进内核 {@code AIConstants}（内核只保留
 * 协议、模型、ROOT 等全局配置），保证能力目录可整体复制拔除。
 *
 * @author hoppinzq
 */
public final class TaskConfig {
    private TaskConfig() {
    }

    // 任务持久化目录名（相对 ROOT，即 <ROOT>/.tasks/）
    public static final String TASKS_DIR_NAME = ".tasks";

    // 任务短 ID 长度（UUID 前 8 位，用于模型引用任务）
    public static final int SHORT_ID_LENGTH = 8;
}
