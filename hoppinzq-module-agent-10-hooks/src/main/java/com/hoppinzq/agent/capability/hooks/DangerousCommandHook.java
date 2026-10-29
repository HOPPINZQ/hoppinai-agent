package com.hoppinzq.agent.capability.hooks;

import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.client.LlmMessage;

import java.util.regex.Pattern;

import static com.hoppinzq.agent.capability.hooks.HooksConfig.DANGER_COMMAND_REGEX;

/**
 * 钩子 1：危险命令护栏（对应原 PermissionHook 的 PreToolUse 语义）。
 * <p>命中危险命令时返回非 null 字符串作为工具结果回灌，跳过真实执行。
 * <p>在新契约里对应 {@link AgentHook#beforeToolCall(LlmMessage.ToolCall)}：
 * 非 null 返回值即"拦截"。
 *
 * @author hoppinzq
 */
public class DangerousCommandHook implements AgentHook {

    private static final Pattern DANGER = Pattern.compile(DANGER_COMMAND_REGEX, Pattern.CASE_INSENSITIVE);

    @Override
    public String beforeToolCall(LlmMessage.ToolCall call) {
        if (call == null || !"bash".equals(call.getName()) || call.getArgumentsJson() == null) {
            return null;
        }
        if (DANGER.matcher(call.getArgumentsJson()).find()) {
            return "[DangerousCommandHook] 拦截危险命令：" + call.getArgumentsJson();
        }
        return null;
    }
}
