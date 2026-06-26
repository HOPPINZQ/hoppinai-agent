package com.hoppinzq.agent.tool.hook;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内置钩子 4：Stop 事件统计每轮工具调用数。
 * <p>
 * 在 Agent 构造时计数器清零；PostToolUse 累加；Stop 触发时输出小结并清零。
 *
 * @author hoppinzq
 */
public class SummaryHook implements HookCallback {

    private final AtomicInteger toolCallsThisTurn = new AtomicInteger(0);

    /** 注册时按事件分别挂载 —— 这是统一入口，按 ctx 内容分流 */
    @Override
    public String apply(HookContext ctx) {
        if (ctx.getToolName() != null && ctx.getToolResult() != null) {
            toolCallsThisTurn.incrementAndGet();
            return null;
        }
        // STOP 事件触发：toolName == null && result == null
        if (ctx.getUserInput() == null && ctx.getToolName() == null) {
            int n = toolCallsThisTurn.getAndSet(0);
            System.out.printf("\u001b[95m[SummaryHook]\u001b[0m 本轮结束，共执行 %d 次工具调用%n", n);
        }
        return null;
    }
}
