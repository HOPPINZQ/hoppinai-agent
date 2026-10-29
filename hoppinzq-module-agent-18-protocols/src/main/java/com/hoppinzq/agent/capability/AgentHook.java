package com.hoppinzq.agent.capability;

import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmResponse;

import java.util.List;

/**
 * Agent 主循环的钩子点。全部为 default 空实现，能力按需覆盖感兴趣的点。
 * <p>挂载位置（见 ZQAgent）：
 * <ul>
 *   <li>{@link #beforeLlmCall} — 每次 provider 调用前（含工具循环内的每一轮）</li>
 *   <li>{@link #afterLlmResponse} — 每次 provider 返回后、消息入库前</li>
 *   <li>{@link #beforeToolCall}/{@link #afterToolCall} — 每个工具调用前后</li>
 * </ul>
 * 同一挂载点上多个能力的执行顺序 = 能力注册顺序。
 *
 * @author hoppinzq
 */
public interface AgentHook {

    /**
     * LLM 调用前。可对上下文做手术：
     * <ul>
     *   <li>原样返回入参 = 不修改</li>
     *   <li>返回新列表 = 用其<b>替换</b>当前上下文（压缩、裁剪类能力的用法；
     *       注意须同步回 Agent 的消息列表，由内核负责）</li>
     * </ul>
     */
    default List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
        return messages;
    }

    /** LLM 返回后、assistant 消息入库前。可读取 usage/finishReason 做记录或干预 */
    default void afterLlmResponse(LlmResponse response) {
    }

    /**
     * 工具执行前。
     *
     * @return null = 放行；非 null 字符串 = 拦截本次调用，并以该字符串作为工具结果回灌
     *         （权限类能力返回 "已拒绝: ..." 之类的文案）
     */
    default String beforeToolCall(LlmMessage.ToolCall call) {
        return null;
    }

    /** 工具执行后（含被拦截的调用，result 为实际回灌给模型的文本） */
    default void afterToolCall(String toolName, String result) {
    }
}
