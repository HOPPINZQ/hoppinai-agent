package com.hoppinzq.agent.capability.hooks;

import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.client.LlmMessage;

import static com.hoppinzq.agent.capability.hooks.HooksConfig.LOG_TRUNCATE_CHARS;

/**
 * 钩子 2：审计日志（对应原 LogHook）。
 * <p>挂 pre / post 两个工具挂载点，把每次工具调用的入参与结果打到控制台，便于排障。
 * 只读旁路：beforeToolCall 恒返回 null（不拦截）。
 *
 * @author hoppinzq
 */
public class AuditLogHook implements AgentHook {

    @Override
    public String beforeToolCall(LlmMessage.ToolCall call) {
        System.out.println("\u001b[90m" + "[log]" + "\u001b[0m" + " pre " + call.getName()
                + "(" + truncate(call.getArgumentsJson()) + ")");
        return null;
    }

    @Override
    public void afterToolCall(String toolName, String result) {
        System.out.println("\u001b[90m" + "[log]" + "\u001b[0m" + " post " + toolName
                + " -> " + truncate(result));
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= LOG_TRUNCATE_CHARS ? s : s.substring(0, LOG_TRUNCATE_CHARS) + "...";
    }
}
