package com.hoppinzq.agent.client;

import com.hoppinzq.agent.constant.AIConstants;

import java.time.Duration;

/**
 * {@link LlmProvider} 工厂：按 {@link AIConstants#PROVIDER} 创建对应协议的实现。
 * <p>两种实现共用同一把 {@code DEEPSEEK_API_KEY}，仅端点不同：
 * <ul>
 *   <li>{@code openai}（默认）→ {@link AIConstants#OPENAI_BASE_URL}</li>
 *   <li>{@code anthropic} → {@link AIConstants#ANTHROPIC_BASE_URL}</li>
 * </ul>
 *
 * @author hoppinzq
 */
public final class LlmProviders {

    private LlmProviders() {
    }

    public static LlmProvider create() {
        Duration timeout = Duration.ofSeconds(AIConstants.TIMEOUT);
        if ("anthropic".equalsIgnoreCase(AIConstants.PROVIDER)) {
            return new AnthropicProvider(AIConstants.ANTHROPIC_BASE_URL, AIConstants.API_KEY, timeout, AIConstants.MAX_RETRIES);
        }
        return new OpenAIProvider(AIConstants.OPENAI_BASE_URL, AIConstants.API_KEY, timeout, AIConstants.MAX_RETRIES);
    }
}
