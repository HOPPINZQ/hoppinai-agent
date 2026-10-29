package com.hoppinzq.agent.capability.react;

/**
 * ReAct 能力自持的配置。
 * <p>属于 react 能力的特性常量，不进内核 {@code AIConstants}，
 * 保证能力目录可整体复制拔除。
 *
 * @author hoppinzq
 */
public final class ReactConfig {
    private ReactConfig() {
    }

    /** 是否启用 ReAct 提示词（false 时退化为普通系统提示） */
    public static final boolean REACT_ENABLE = true;
}