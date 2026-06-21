package com.hoppinzq.agent.tool.recovery;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * LLM 调用重试包装器。三条恢复路径：
 * <ol>
 *   <li><b>输出截断</b>（stop_reason == max_tokens）：升级 maxTokens 8K → 64K 再续写</li>
 *   <li><b>上下文溢出</b>（prompt_too_long）：触发 {@link ReactiveCompactor#compact}</li>
 *   <li><b>瞬态错误</b>（429 / 529 / 网络）：指数退避 min(500×2^attempt, 32000) + jitter，
 *       连续 3 次 529 后切 fallback model</li>
 * </ol>
 *
 * <p>调用方传 {@code paramBuilder(RecoveryState) -> MessageCreateParams.Builder}，
 * 让 wrapper 在重试时按 state 调整 maxTokens / model。
 *
 * @author hoppinzq
 */
public class RetryWrapper {

    private static final long BASE_DELAY_MS = 500L;
    private static final long MAX_DELAY_MS = 32_000L;

    private final AnthropicClient client;

    public RetryWrapper(AnthropicClient client) {
        this.client = client;
    }

    /**
     * 带恢复策略的 LLM 调用。
     *
     * @param paramBuilder 接收当前 RecoveryState，返回待用的 params builder
     *                     （wrapper 会据此调整 model / maxTokens）
     * @param state        会话级恢复状态（maxTokens / model / 计数器）
     * @param history      当 prompt_too_long 时用于紧急压缩
     */
    public Message call(Function<RecoveryState, MessageCreateParams.Builder> paramBuilder,
                        RecoveryState state,
                        List<MessageParam> history) {
        int localAttempt = 0;
        while (true) {
            try {
                MessageCreateParams params = paramBuilder.apply(state)
                        .maxTokens(state.getMaxTokens())
                        .build();
                Message msg = client.messages().create(params);

                // 路径 1：输出截断
                if (isTruncated(msg)) {
                    System.out.printf("\u001b[95m[recovery]\u001b[0m 输出截断 (max_tokens)，当前上限 %d%n",
                            state.getMaxTokens());
                    if (state.upgradeMaxTokens()) {
                        System.out.printf("\u001b[95m[recovery]\u001b[0m 升级 maxTokens → %d 并续写%n",
                                state.getMaxTokens());
                        continue;
                    }
                    System.out.println("\u001b[95m[recovery]\u001b[0m maxTokens 已到上限，接受截断");
                }
                state.reset();
                return msg;
            } catch (Exception e) {
                localAttempt++;
                state.incrementAttempt();
                ErrorClassifier.RecoveryAction action = ErrorClassifier.classify(e);
                System.out.printf("\u001b[91m[recovery]\u001b[0m attempt=%d, action=%s, err=%s%n",
                        localAttempt, action, brief(e));

                switch (action) {
                    case FATAL -> throw e;
                    case PROMPT_TOO_LONG -> {
                        boolean compacted = ReactiveCompactor.compact(history);
                        if (!compacted) {
                            System.out.println("\u001b[91m[recovery]\u001b[0m 已无历史可压缩，放弃");
                            throw e;
                        }
                    }
                    case OVERLOAD -> {
                        if (state.increment529AndCheckSwitch()) {
                            System.out.printf("\u001b[95m[recovery]\u001b[0m 连续 529，切到 fallback 模型 %s%n",
                                    state.getFallbackModel());
                            state.setModel(state.getFallbackModel());
                        }
                        backoff(localAttempt);
                    }
                    case RATE_LIMIT, TRANSIENT -> backoff(localAttempt);
                }

                if (localAttempt >= RecoveryState.MAX_ATTEMPTS) {
                    System.out.println("\u001b[91m[recovery]\u001b[0m 重试次数耗尽，抛出");
                    throw e;
                }
            }
        }
    }

    private boolean isTruncated(Message msg) {
        try {
            if (msg.stopReason() == null) {
                return false;
            }
            String s = msg.stopReason().toString();
            return s.toLowerCase().contains("max_tokens");
        } catch (Exception e) {
            return false;
        }
    }

    private void backoff(int attempt) {
        long base = Math.min(BASE_DELAY_MS * (1L << Math.min(attempt, 6)), MAX_DELAY_MS);
        long jitter = ThreadLocalRandom.current().nextLong(0, base / 2 + 1);
        long delay = base + jitter;
        System.out.printf("\u001b[95m[recovery]\u001b[0m 退避 %dms（base=%d, jitter=%d）%n",
                delay, base, jitter);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String brief(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) {
            return t.getClass().getSimpleName();
        }
        return msg.length() > 120 ? msg.substring(0, 120) + "..." : msg;
    }
}
