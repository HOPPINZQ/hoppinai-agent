package com.hoppinzq.agent.capability.teams;

/**
 * teams 能力的自持配置（替代原 AIConstants 中的特性常量；06 权威源 CompactConfig 同款做法）。
 *
 * @author hoppinzq
 */
public final class TeamsConfig {

    private TeamsConfig() {
    }

    /** teammate 的 LLM 采样温度（原 AIConstants.TEMPERATURE，仅 teammate 循环使用） */
    public static final double TEMPERATURE = 0.5;

    /** teammate 单次任务的最大 LLM 轮数，超限强制收尾，防止守护线程跑飞 */
    public static final int TEAMMATE_MAX_ROUNDS = 10;

    /** 邮箱根目录（相对 ROOT）：每个成员一个 {name}.jsonl 收件箱 */
    public static final String MAILBOX_DIR = ".mailboxes";

    /** lead 的邮箱名（teammate 通过它向负责人回执） */
    public static final String LEAD_NAME = "lead";
}
