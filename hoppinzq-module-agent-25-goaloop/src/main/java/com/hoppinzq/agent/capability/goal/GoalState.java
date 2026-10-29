package com.hoppinzq.agent.capability.goal;

import lombok.Builder;
import lombok.Data;

/**
 * 活跃 Goal 的状态：条件、判断次数、开始时间与最近一次判断原因。
 * <p>对应 Python s17 的 {@code GoalState}。
 *
 * @author hoppinzq
 */
@Data
@Builder
public class GoalState {
    /** 完成条件（用户通过 /goal 输入的原文） */
    private String condition;
    /** 判断器已运行的次数 */
    private int iterations;
    /** 目标设置时间（epoch millis） */
    private long setAtEpochMs;
    /** 设置目标时的会话累计 token（用于统计本次目标消耗） */
    private long tokensAtStart;
    /** 最近一次判断原因 */
    private String lastReason;

    /** 已经过去的秒数 */
    public long elapsedSeconds() {
        return Math.max(0, (System.currentTimeMillis() - setAtEpochMs) / 1000);
    }
}
