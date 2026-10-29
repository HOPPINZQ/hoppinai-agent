package com.hoppinzq.agent.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次 LLM 调用的 token 使用统计。
 * <p>对应 SDK 的 {@code Usage} 对象，只保留可序列化的字段。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenUsage {
    /**
     * 输入 token 数（必选）
     */
    private Long inputTokens;

    /**
     * 输出 token 数（必选）
     */
    private Long outputTokens;

    /**
     * 缓存命中 token 数（prompt caching 读命中）
     */
    private Long cacheReadTokens;

    /**
     * 缓存创建 token 数（新写入缓存）
     */
    private Long cacheCreationTokens;

    /**
     * 产生此 usage 的时间戳（便于分析不同时段的消耗）
     */
    private String timestamp;

    /**
     * 计算本次调用的总 token 数
     */
    public long getTotalTokens() {
        long total = inputTokens != null ? inputTokens : 0;
        total += outputTokens != null ? outputTokens : 0;
        return total;
    }

    /**
     * 计算缓存命中率：缓存命中 / 实际输入。
     * <p>注：OpenAI API 的 {@code prompt_tokens_details.cached_tokens} 是
     * {@code prompt_tokens} 的子集（DeepSeek 同样如此），与 Anthropic 的独立统计语义不同。
     */
    public double getCacheHitRate() {
        long cached = cacheReadTokens != null ? cacheReadTokens : 0;
        long input = inputTokens != null ? inputTokens : 0;
        if (input == 0) {
            return 0.0;
        }
        return (double) cached / input;
    }
}
