package com.hoppinzq.agent.capability.goal;

import com.hoppinzq.agent.client.LlmMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hoppinzq.agent.capability.goal.GoalConfig.GOAL_MAX_LENGTH;
import static com.hoppinzq.agent.capability.goal.GoalConfig.GOAL_STOP_HOOK_BLOCK_CAP;

/**
 * 会话级 Goal 状态 + Stop hook 裁决。
 *
 * <p>每个会话同时只有一个活跃 Goal。主模型负责干活；是否真的完成，
 * 由 {@link GoalEvaluator}（另一次独立模型调用）在每轮结束处判断：
 * 未满足就把理由回灌到同一份 messages 继续下一轮，不设单独的 continuation queue。
 *
 * <p>自动继续必须有出口：
 * <ul>
 *   <li>主循环的全局 {@code max_turns}（在 Agent 侧检查）；</li>
 *   <li>Stop hook 连续阻止结束的次数上限 {@link #blockCap}。</li>
 * </ul>
 * 达到上限时把控制权还给用户，但不会把目标伪装成完成，也不会自动清除目标。
 *
 * <p>对应 Python s17 的 {@code GoalController}。
 *
 * @author hoppinzq
 */
public class GoalController {

    /** /goal clear 的别名 */
    public static final Set<String> CLEAR_ALIASES = Set.of("clear", "stop", "off", "reset", "none", "cancel");

    private final GoalEvaluator evaluator;
    private final int blockCap;
    private final List<GoalStatusEvent> events = new ArrayList<>();

    private GoalState active;
    private GoalStatusEvent lastStatus;
    private int consecutiveBlocks = 0;

    public GoalController(GoalEvaluator evaluator) {
        this(evaluator, GOAL_STOP_HOOK_BLOCK_CAP);
    }

    public GoalController(GoalEvaluator evaluator, int blockCap) {
        if (blockCap < 1) {
            throw new GoalException("block_cap must be at least 1");
        }
        this.evaluator = evaluator;
        this.blockCap = blockCap;
    }

    /** 是否有活跃目标 */
    public boolean hasActiveGoal() {
        return active != null;
    }

    /** 当前目标的完成条件（无目标时返回空串） */
    public String activeCondition() {
        return active == null ? "" : active.getCondition();
    }

    /** 新一次查询开始：重置连续阻止计数 */
    public void beginQuery() {
        this.consecutiveBlocks = 0;
    }

    /**
     * 设置（或替换）目标。
     *
     * @param condition     完成条件
     * @param tokensAtStart 设置时的会话累计 token
     * @return 新的 GoalState
     */
    public GoalState setGoal(String condition, long tokensAtStart) {
        String trimmed = condition == null ? "" : condition.trim();
        if (trimmed.isEmpty()) {
            throw new GoalException("goal condition cannot be empty");
        }
        if (trimmed.length() > GOAL_MAX_LENGTH) {
            throw new GoalException("goal condition cannot exceed " + GOAL_MAX_LENGTH + " characters");
        }
        if (active != null) {
            record(false, false, false, "replaced by a new goal");
        }
        this.active = GoalState.builder()
                .condition(trimmed)
                .iterations(0)
                .setAtEpochMs(System.currentTimeMillis())
                .tokensAtStart(tokensAtStart)
                .build();
        this.consecutiveBlocks = 0;
        record(true, false, false, "goal set");
        return active;
    }

    /**
     * 清除当前目标。
     *
     * @return 结果描述；无目标时返回 "No goal set"
     */
    public String clear(String reason) {
        if (active == null) {
            return "No goal set";
        }
        String condition = active.getCondition();
        record(false, false, false, reason);
        this.active = null;
        this.consecutiveBlocks = 0;
        return "Goal cleared: " + condition;
    }

    /**
     * 目标状态报告（/goal 命令查看）。
     *
     * @param currentTokens 当前会话累计 token
     */
    public String status(long currentTokens) {
        if (active == null) {
            if (lastStatus != null && lastStatus.met()) {
                return "Goal achieved: " + lastStatus.condition() + "\nReason: " + lastStatus.reason();
            }
            if (lastStatus != null && lastStatus.failed()) {
                return "Goal failed: " + lastStatus.condition() + "\nReason: " + lastStatus.reason();
            }
            return "No goal set";
        }
        long spent = Math.max(0, currentTokens - active.getTokensAtStart());
        StringBuilder sb = new StringBuilder();
        sb.append("Goal active: ").append(active.getCondition()).append('\n');
        sb.append("Elapsed: ").append(active.elapsedSeconds()).append("s\n");
        sb.append("Evaluations: ").append(active.getIterations()).append('\n');
        sb.append("Tokens: ").append(spent);
        if (active.getLastReason() != null) {
            sb.append("\nLast reason: ").append(active.getLastReason());
        }
        return sb.toString();
    }

    /**
     * Stop hook：本轮模型不再调用工具时判断目标是否达成。
     *
     * @param messages           当前对话记录
     * @param backgroundRunning  是否仍有后台任务在运行（未结束时先 defer，不判断）
     * @return 裁决；{@code block} 时调用方应把 {@link #continueMessage(GoalDecision)}
     *         追加进对话并继续下一轮
     */
    public GoalDecision evaluateAfterTurn(List<LlmMessage> messages, boolean backgroundRunning) {
        if (active == null) {
            return GoalDecision.allow();
        }
        if (backgroundRunning) {
            return GoalDecision.defer("background work is still running");
        }

        GoalState state = this.active;
        GoalEvaluation evaluation;
        try {
            evaluation = evaluator.evaluate(state.getCondition(), messages);
        } catch (Exception e) {
            // 判断器调用失败：停止自动续轮、保留目标，把错误交给用户
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            state.setLastReason(reason);
            record(true, false, false, reason);
            return GoalDecision.error(reason);
        }

        state.setIterations(state.getIterations() + 1);
        state.setLastReason(evaluation.reason());

        if (evaluation.ok()) {
            record(false, true, false, evaluation.reason());
            this.active = null;
            this.consecutiveBlocks = 0;
            return GoalDecision.achieved(evaluation.reason());
        }

        if (evaluation.impossible()) {
            record(false, false, true, evaluation.reason());
            this.active = null;
            this.consecutiveBlocks = 0;
            return GoalDecision.failed(evaluation.reason());
        }

        this.consecutiveBlocks++;
        record(true, false, false, evaluation.reason());
        if (this.consecutiveBlocks > blockCap) {
            return GoalDecision.limit(
                    "goal remains active, but the Stop hook blocked " + blockCap + " consecutive turns");
        }
        return GoalDecision.block(evaluation.reason());
    }

    /**
     * 构造回灌对话的继续消息（block 时使用）。
     */
    public String continueMessage(GoalDecision decision) {
        return "[Goal still active]\n"
                + "Condition: " + activeCondition() + "\n"
                + "Evaluator: " + decision.reason() + "\n"
                + "Continue working and surface the missing evidence.";
    }

    /** goal_status 事件记录 */
    private void record(boolean active, boolean met, boolean failed, String reason) {
        GoalState state = this.active;
        GoalStatusEvent event = new GoalStatusEvent(
                "goal_status",
                state == null ? (lastStatus != null ? lastStatus.condition() : "") : state.getCondition(),
                active, met, failed, reason,
                state == null ? 0 : state.getIterations(),
                state == null ? 0 : state.elapsedSeconds());
        this.events.add(event);
        this.lastStatus = event;
    }

    public List<GoalStatusEvent> getEvents() {
        return new ArrayList<>(events);
    }

    /**
     * 从宿主保存的 goal_status 事件中恢复仍然活跃的 Goal。
     * 已完成、失败或主动清除的 Goal 不会重新启动；
     * 恢复后保留完成条件，但重新计算轮数、时间和 token 使用量。
     */
    public static GoalController restore(GoalEvaluator evaluator, List<GoalStatusEvent> events, int blockCap) {
        GoalController controller = new GoalController(evaluator, blockCap);
        if (events != null) {
            controller.events.addAll(events);
            for (int i = events.size() - 1; i >= 0; i--) {
                GoalStatusEvent event = events.get(i);
                if (!"goal_status".equals(event.type())) {
                    continue;
                }
                controller.lastStatus = event;
                if (event.active()) {
                    controller.active = GoalState.builder()
                            .condition(event.condition())
                            .iterations(0)
                            .setAtEpochMs(System.currentTimeMillis())
                            .tokensAtStart(0)
                            .build();
                }
                break;
            }
        }
        return controller;
    }

    /**
     * goal_status 事件（用于宿主持久化与 {@link #restore}）。
     */
    public record GoalStatusEvent(
            String type,
            String condition,
            boolean active,
            boolean met,
            boolean failed,
            String reason,
            int iterations,
            long durationSeconds) {
    }
}
