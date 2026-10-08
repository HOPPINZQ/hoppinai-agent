package com.hoppinzq.agent.client;

import com.hoppinzq.agent.session.TokenUsage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 协议中立的 LLM 响应。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmResponse {
    /** 回复消息（assistant 角色：text + toolCalls） */
    private LlmMessage message;
    /** 结束原因 */
    private FinishReason finishReason;
    /** token 使用统计；可能为 null（部分后端不返回） */
    private TokenUsage usage;

    /**
     * 中立化的结束原因。
     * <ul>
     *   <li>{@code STOP} —— 正常结束（anthropic: end_turn/stop_sequence；openai: stop/content_filter 等）</li>
     *   <li>{@code LENGTH} —— 被 max_tokens 截断（openai: length）</li>
     *   <li>{@code TOOL_CALLS} —— 需要执行工具后继续（anthropic: tool_use；openai: tool_calls）</li>
     * </ul>
     */
    public enum FinishReason {
        STOP,
        LENGTH,
        TOOL_CALLS
    }
}
