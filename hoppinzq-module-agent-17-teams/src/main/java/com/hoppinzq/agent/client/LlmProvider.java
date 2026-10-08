package com.hoppinzq.agent.client;

import com.hoppinzq.agent.session.TokenUsage;

/**
 * LLM 服务提供方接口。协议差异（Anthropic / OpenAI）全部封装在实现类内，
 * 框架核心（ZQAgent、工具、会话）只面向本接口与中立消息模型编程。
 *
 * @author hoppinzq
 */
public interface LlmProvider {

    /**
     * 阻塞式补全。
     */
    LlmResponse complete(LlmRequest request);

    /**
     * 流式补全。文本增量实时回调；工具调用在块结束时整块回调一次（入参为累积后的完整 JSON）。
     * <p>默认不支持，仅实现了流式的 Provider 可用。
     */
    default void stream(LlmRequest request, StreamListener listener) {
        throw new UnsupportedOperationException("当前 Provider 不支持流式输出");
    }

    /**
     * token 计数。Anthropic 走原生 countTokens 接口（精确值）；
     * OpenAI 协议无原生接口，实现类通常用 jtokkit 估算（近似值）。
     */
    default int countTokens(LlmRequest request) {
        throw new UnsupportedOperationException("当前 Provider 不支持 token 计数");
    }

    /**
     * 流式回调。全部为默认方法，消费方按需覆盖。
     */
    interface StreamListener {

        /** 流开始（收到消息开始事件） */
        default void onStart() {
        }

        /** 文本增量 */
        default void onTextDelta(String delta) {
        }

        /** 工具调用块结束：argumentsJson 为累积后的完整入参 JSON */
        default void onToolUse(String id, String name, String argumentsJson) {
        }

        /** 流正常结束 */
        default void onComplete(LlmResponse.FinishReason finishReason, TokenUsage usage) {
        }

        /** 流异常 */
        default void onError(Throwable t) {
        }
    }
}
