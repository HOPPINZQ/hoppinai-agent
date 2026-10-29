package com.hoppinzq.agent.capability.goal;

/**
 * Goal Loop 能力自持配置（对应 Python s17 的 goal 相关配置）。
 * <p>原 {@code AIConstants} 中的 {@code GOAL_*} / {@code MAX_TURNS} 属于目标循环特性常量，
 * 随能力化移入本类：内核常量类不再感知目标循环，拔除本能力目录时这些配置一并消失。
 *
 * @author hoppinzq
 */
public final class GoalConfig {
    private GoalConfig() {
    }

    /** goal 完成条件最大长度 */
    public static final int GOAL_MAX_LENGTH = 4000;

    /** 判断器（独立模型调用）的最大输出 token */
    public static final int GOAL_EVALUATOR_MAX_TOKENS = 512;

    /** Stop hook 连续阻止结束的次数上限；达到上限把控制权还给用户（目标保持活跃） */
    public static final int GOAL_STOP_HOOK_BLOCK_CAP = 8;

    /** 送给判断器的对话文本上限：保留最近的完整消息，过长的新消息只保留开头结尾 */
    public static final int GOAL_TRANSCRIPT_MAX_CHARS = 24000;

    /**
     * Goal Gate 连续续轮上限（环境变量 {@code MAX_TURNS}，0 或未设置 = 不限制）。
     * <p>语义适配说明：内核主循环不暴露"每次提交的模型调用计数"挂载点，
     * 此上限在能力侧用于限制 Goal Gate 连续自动续轮的次数（原来的全局轮数上限的等价收窄）。
     */
    public static final int MAX_TURNS = Integer.getInteger("MAX_TURNS", 0);

    /** 判断器模型（环境变量 {@code GOAL_EVALUATOR_MODEL_ID}，未设置时用主模型） */
    public static String evaluatorModel(String fallback) {
        return System.getenv().getOrDefault("GOAL_EVALUATOR_MODEL_ID", fallback);
    }
}
