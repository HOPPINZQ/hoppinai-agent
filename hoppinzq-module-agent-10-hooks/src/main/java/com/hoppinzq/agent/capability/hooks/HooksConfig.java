package com.hoppinzq.agent.capability.hooks;

/**
 * hooks 能力自持的配置常量。
 * <p>属于 hooks 能力的特性常量，不进内核 {@code AIConstants}，
 * 保证能力目录可整体复制拔除。
 *
 * @author hoppinzq
 */
public final class HooksConfig {
    private HooksConfig() {
    }

    // LargeOutputHook：工具结果超过该字符数即告警（原 tool/hook/LargeOutputHook.MAX）
    public static final int LARGE_OUTPUT_MAX_CHARS = 4000;

    // LogHook：审计日志里单条内容的截断长度
    public static final int LOG_TRUNCATE_CHARS = 80;

    // PermissionHook：危险命令黑名单（命中即拦截 bash 调用）
    public static final String DANGER_COMMAND_REGEX =
            "rm\\s+-rf\\s+/|sudo|^\\s*del\\s+/[a-z]*f.*C:|format\\s+[A-Z]:";
}
