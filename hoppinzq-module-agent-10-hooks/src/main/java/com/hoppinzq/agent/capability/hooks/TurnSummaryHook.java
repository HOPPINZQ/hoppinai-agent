package com.hoppinzq.agent.capability.hooks;

import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.client.LlmResponse;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 钩子 4：每轮工具调用数统计（对应原 SummaryHook 的 PostToolUse + Stop 语义）。
 * <p>afterToolCall 累加；一次 LLM 回复以非 tool_calls 结束时视为"本轮结束"，
 * 输出小结并清零。
 *
 * @author hoppinzq
 */
public class TurnSummaryHook implements AgentHook {

    private final AtomicInteger toolCallsThisTurn = new AtomicInteger(0);

    @Override
    public void afterToolCall(String toolName, String result) {
        toolCallsThisTurn.incrementAndGet();
    }

    @Override
    public void afterLlmResponse(LlmResponse response) {
        if (response == null || response.getFinishReason() == LlmResponse.FinishReason.TOOL_CALLS) {
            return; // 工具循环尚未结束，本轮继续
        }
        int n = toolCallsThisTurn.getAndSet(0);
        System.out.printf("\u001b[95m" + "[TurnSummaryHook]" + "\u001b[0m" + " 本轮结束，共执行 %d 次工具调用%n", n);
    }
}
