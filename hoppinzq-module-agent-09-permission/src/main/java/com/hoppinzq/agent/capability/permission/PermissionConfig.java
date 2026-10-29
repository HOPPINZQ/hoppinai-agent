package com.hoppinzq.agent.capability.permission;

/**
 * permission 能力自持的配置常量。
 * <p>属于 permission 能力的特性常量，不进内核 {@code AIConstants}，
 * 保证能力目录可整体复制拔除。
 *
 * @author hoppinzq
 */
public final class PermissionConfig {
    private PermissionConfig() {
    }

    // 拦截工具调用时回灌给模型的文案前缀（AgentHook.beforeToolCall 的非 null 返回值整体作为工具结果）
    public static final String DENY_MESSAGE_PREFIX = "已拒绝: ";
}
