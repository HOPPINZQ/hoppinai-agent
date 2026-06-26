package com.hoppinzq.agent.tool.hook;

/**
 * 内置钩子 2：审计日志。
 * <p>
 * 订阅全部 4 个事件，把每次工具调用 / 用户输入打到控制台，便于排障。
 * 永远返回 null（只读旁路）。
 *
 * @author hoppinzq
 */
public class LogHook implements HookCallback {

    @Override
    public String apply(HookContext ctx) {
        // LogHook 注册到 4 个事件上，由调用方决定；这里只输出通用审计信息
        if (ctx.getUserInput() != null) {
            System.out.printf("\u001b[90m[log]\u001b[0m user_prompt: %s%n",
                    truncate(ctx.getUserInput(), 80));
        } else if (ctx.getToolResult() != null) {
            System.out.printf("\u001b[90m[log]\u001b[0m post %s -> %s%n",
                    ctx.getToolName(), truncate(ctx.getToolResult(), 80));
        } else if (ctx.getToolName() != null) {
            System.out.printf("\u001b[90m[log]\u001b[0m pre %s(%s)%n",
                    ctx.getToolName(), truncate(ctx.getToolInput(), 80));
        } else {
            System.out.println("\u001b[90m[log]\u001b[0m stop event (一轮结束)");
        }
        return null;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
