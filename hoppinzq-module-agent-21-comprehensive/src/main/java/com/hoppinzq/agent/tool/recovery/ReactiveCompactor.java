package com.hoppinzq.agent.tool.recovery;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageParam;

import java.util.ArrayList;
import java.util.List;

/**
 * 紧急上下文压缩器（reactive compact）。
 * <p>
 * 触发条件：API 返回 prompt_too_long。策略：
 * <ol>
 *   <li>保留首条 user message（任务目标不能丢）</li>
 *   <li>保留最近 N 条消息（{@link #KEEP_RECENT}）</li>
 *   <li>中间全部用一条 "（已省略 N 条历史消息）" 摘要替代</li>
 * </ol>
 * 这是"紧急"压缩 —— 优先级是立刻能继续跑，不追求摘要质量。
 * 持续压缩请见 module-06 的主动 compact 实现。
 *
 * @author hoppinzq
 */
public final class ReactiveCompactor {

    /** 保留最近 N 条消息 */
    public static final int KEEP_RECENT = 6;

    private ReactiveCompactor() {
    }

    /**
     * 原地压缩 messageParams；返回是否真的发生了压缩。
     */
    public static boolean compact(List<MessageParam> messageParams) {
        if (messageParams.size() <= KEEP_RECENT + 1) {
            return false;
        }
        MessageParam first = messageParams.get(0);
        int total = messageParams.size();
        int dropStart = 1;
        int dropEnd = total - KEEP_RECENT;
        int dropped = dropEnd - dropStart;

        List<MessageParam> kept = new ArrayList<>();
        kept.add(first);

        // 插入一条摘要
        kept.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("[系统] 已省略 " + dropped + " 条历史消息以释放上下文空间，请继续。")
                .build());

        // 追加最近 KEEP_RECENT 条
        for (int i = dropEnd; i < total; i++) {
            kept.add(messageParams.get(i));
        }

        messageParams.clear();
        messageParams.addAll(kept);

        System.out.printf("\u001b[95m[reactive compact]\u001b[0m 保留首条 + 最近 %d 条，省略 %d 条%n",
                KEEP_RECENT, dropped);
        return true;
    }

    /** 估算 messageParams 的字符长度（粗略，用于诊断） */
    public static int approximateLength(List<MessageParam> messageParams) {
        int n = 0;
        for (MessageParam p : messageParams) {
            n += extractText(p).length();
        }
        return n;
    }

    private static String extractText(MessageParam p) {
        StringBuilder sb = new StringBuilder();
        // MessageParam.content() 可能是 String 或 BlockParams；这里只尽力提取
        try {
            Object content = p._content();
            if (content instanceof String s) {
                return s;
            }
            if (content instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof ContentBlockParam cbp) {
                        sb.append(cbp.toString());
                    } else {
                        sb.append(String.valueOf(o));
                    }
                }
            }
        } catch (Exception ignore) {
            // best-effort
        }
        return sb.toString();
    }

    /** 仅供消除未用导入警告，实际不调用 */
    @SuppressWarnings("unused")
    private static void unused() {
    }
}
