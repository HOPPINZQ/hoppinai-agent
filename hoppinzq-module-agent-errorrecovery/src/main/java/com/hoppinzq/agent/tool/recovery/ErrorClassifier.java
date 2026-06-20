package com.hoppinzq.agent.tool.recovery;

/**
 * 错误分类。
 * <p>
 * 把 LLM 调用抛出的异常映射到三种恢复路径：
 * <ul>
 *   <li>{@link #PROMPT_TOO_LONG}  → 触发 {@link ReactiveCompactor} 紧急压缩历史</li>
 *   <li>{@link #RATE_LIMIT}       → 429，指数退避</li>
 *   <li>{@link #OVERLOAD}         → 529，指数退避 + 连续命中切 fallback model</li>
 *   <li>{@link #TRANSIENT}        → 网络抖动等，指数退避</li>
 *   <li>{@link #FATAL}            → 其它不可恢复，直接抛出</li>
 * </ul>
 *
 * <p>判断依据：异常 message / toString 中是否包含特定子串（Anthropic SDK 的异常通常带状态码描述）。
 *
 * @author hoppinzq
 */
public final class ErrorClassifier {

    public enum RecoveryAction {
        PROMPT_TOO_LONG,
        RATE_LIMIT,
        OVERLOAD,
        TRANSIENT,
        FATAL
    }

    private ErrorClassifier() {
    }

    public static RecoveryAction classify(Throwable t) {
        if (t == null) {
            return RecoveryAction.FATAL;
        }
        String msg = (t.getMessage() == null ? "" : t.getMessage()).toLowerCase();
        String cls = t.getClass().getName().toLowerCase();
        String all = msg + " " + cls + " " + causeChain(t);

        if (all.contains("prompt is too long") || all.contains("prompt_too_long")
                || all.contains("context length") || all.contains("context_length")) {
            return RecoveryAction.PROMPT_TOO_LONG;
        }
        if (all.contains("529") || all.contains("overloaded")) {
            return RecoveryAction.OVERLOAD;
        }
        if (all.contains("429") || all.contains("rate limit") || all.contains("rate_limit")) {
            return RecoveryAction.RATE_LIMIT;
        }
        // 网络抖动：timeout / connection / socket / interrupted
        if (all.contains("timeout") || all.contains("timed out")
                || all.contains("connection") || all.contains("socket")
                || all.contains("interrupted") || all.contains("broken pipe")
                || all.contains("unknownhost")) {
            return RecoveryAction.TRANSIENT;
        }
        return RecoveryAction.FATAL;
    }

    private static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable c = t.getCause();
        int depth = 0;
        while (c != null && depth++ < 5) {
            sb.append(" ").append(c.getClass().getName().toLowerCase());
            if (c.getMessage() != null) {
                sb.append(" ").append(c.getMessage().toLowerCase());
            }
            c = c.getCause();
        }
        return sb.toString();
    }
}
