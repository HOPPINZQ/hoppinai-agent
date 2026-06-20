package com.hoppinzq.agent.tool.hook;

import java.util.regex.Pattern;

/**
 * 内置钩子 1：用 PreToolUse 实现的轻量权限护栏。
 * <p>
 * 不再硬编码在 ZQAgent 内部 —— 而是作为一条可注册的钩子。
 * 命中危险命令时返回非 null 字符串作为 ToolResult 回灌，跳过真实执行。
 *
 * @author hoppinzq
 */
public class PermissionHook implements HookCallback {

    private static final Pattern DANGER = Pattern.compile(
            "rm\\s+-rf\\s+/|sudo|^\\s*del\\s+/[a-z]*f.*C:|format\\s+[A-Z]:",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String apply(HookContext ctx) {
        if (!"bash".equals(ctx.getToolName())) {
            return null;
        }
        if (ctx.getToolInput() == null) {
            return null;
        }
        if (DANGER.matcher(ctx.getToolInput()).find()) {
            return "[PermissionHook] 拦截危险命令：" + ctx.getToolInput();
        }
        return null;
    }
}
