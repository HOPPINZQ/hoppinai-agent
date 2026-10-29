package com.hoppinzq.agent.capability.goal;

/**
 * 判断器的一次判断结果。
 * <ul>
 *   <li>{@code ok=true}  — 完成条件已由对话中的证据满足</li>
 *   <li>{@code ok=false} — 还要继续；{@code impossible=true} 表示目标已无法完成</li>
 * </ul>
 * 对应 Python s17 的 {@code GoalEvaluation}。
 *
 * @param ok         完成条件是否满足
 * @param reason     判断理由（未满足时说明还缺什么）
 * @param impossible 目标是否已无法完成
 * @author hoppinzq
 */
public record GoalEvaluation(boolean ok, String reason, boolean impossible) {
}
