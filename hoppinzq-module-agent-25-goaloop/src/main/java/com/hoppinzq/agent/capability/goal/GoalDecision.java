package com.hoppinzq.agent.capability.goal;

/**
 * Stop hook 的裁决：本轮模型不再调用工具时，目标是否允许结束。
 * <p>对应 Python s17 的 {@code StopDecision}。动作语义：
 * <ul>
 *   <li>{@code allow}    — 无活跃目标，放行（退出条件与基础循环一致）</li>
 *   <li>{@code block}    — 条件未满足，把理由回灌对话并继续下一轮</li>
 *   <li>{@code achieved} — 条件已满足，正常结束</li>
 *   <li>{@code failed}   — 判断器认定无法完成，结束（不伪装成功）</li>
 *   <li>{@code limit}    — 连续阻止次数超上限，把控制权还给用户（目标保持活跃）</li>
 *   <li>{@code defer}    — 后台任务未结束，暂不判断</li>
 *   <li>{@code error}    — 判断器调用失败，停止自动续轮并交还用户</li>
 * </ul>
 *
 * @param action 动作
 * @param reason 理由（block 时回灌对话；其他动作时展示给用户）
 * @author hoppinzq
 */
public record GoalDecision(String action, String reason) {

    public static final String ALLOW = "allow";
    public static final String BLOCK = "block";
    public static final String ACHIEVED = "achieved";
    public static final String FAILED = "failed";
    public static final String LIMIT = "limit";
    public static final String DEFER = "defer";
    public static final String ERROR = "error";

    public static GoalDecision allow() {
        return new GoalDecision(ALLOW, "");
    }

    public static GoalDecision defer(String reason) {
        return new GoalDecision(DEFER, reason);
    }

    public static GoalDecision error(String reason) {
        return new GoalDecision(ERROR, reason);
    }

    public static GoalDecision achieved(String reason) {
        return new GoalDecision(ACHIEVED, reason);
    }

    public static GoalDecision failed(String reason) {
        return new GoalDecision(FAILED, reason);
    }

    public static GoalDecision limit(String reason) {
        return new GoalDecision(LIMIT, reason);
    }

    public static GoalDecision block(String reason) {
        return new GoalDecision(BLOCK, reason);
    }

    public boolean isBlock() {
        return BLOCK.equals(action);
    }
}
