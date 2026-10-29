package com.hoppinzq.agent.capability.hooks;

import com.hoppinzq.agent.capability.AgentHook;

import static com.hoppinzq.agent.capability.hooks.HooksConfig.LARGE_OUTPUT_MAX_CHARS;

/**
 * 钩子 3：输出过长告警（对应原 LargeOutputHook 的 PostToolUse 语义）。
 * <p>工具输出过长会撑爆上下文窗口。本钩子在结果回灌后检查长度，
 * 超限时打印告警（真实工程中可改由 compact 类能力做硬截断/持久化）。
 *
 * @author hoppinzq
 */
public class LargeOutputHook implements AgentHook {

    @Override
    public void afterToolCall(String toolName, String result) {
        if (result == null || result.length() <= LARGE_OUTPUT_MAX_CHARS) {
            return;
        }
        int dropped = result.length() - LARGE_OUTPUT_MAX_CHARS;
        System.out.printf("\u001b[93m" + "[LargeOutputHook]" + "\u001b[0m" + " %s 输出过长（%d 字符，超出 %d）%n",
                toolName, result.length(), dropped);
    }
}
