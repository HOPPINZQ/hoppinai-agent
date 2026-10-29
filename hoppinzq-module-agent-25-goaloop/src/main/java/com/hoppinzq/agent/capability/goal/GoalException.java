package com.hoppinzq.agent.capability.goal;

/**
 * Goal 命令或判断器无法安全使用时抛出。
 * <p>对应 Python s17 的 {@code GoalError}。
 *
 * @author hoppinzq
 */
public class GoalException extends RuntimeException {
    public GoalException(String message) {
        super(message);
    }

    public GoalException(String message, Throwable cause) {
        super(message, cause);
    }
}
