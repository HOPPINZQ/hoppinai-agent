package com.hoppinzq.agent.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子智能体执行结果
 * 包含执行结果和 token 使用情况
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentSessionResult {
    /**
     * 执行结果
     */
    private String result;

    /**
     * 输入 token 数量
     */
    private long inputTokens = 0;

    /**
     * 输出 token 数量
     */
    private long outputTokens = 0;

    /**
     * 创建成功的子智能体结果
     */
    public static SubAgentSessionResult success(String result, long inputTokens, long outputTokens) {
        return new SubAgentSessionResult(result, inputTokens, outputTokens);
    }

    /**
     * 创建错误的子智能体结果
     */
    public static SubAgentSessionResult error(String errorMsg) {
        return new SubAgentSessionResult(errorMsg, 0, 0);
    }

    /**
     * 获取总 token 数量
     */
    public long getTotalTokens() {
        return inputTokens + outputTokens;
    }

    /**
     * 格式化 token 使用情况
     */
    public String formatTokenUsage() {
        return String.format("子智能体 Token 使用: 输入=%d, 输出=%d, 总计=%d",
                inputTokens, outputTokens, getTotalTokens());
    }
}
