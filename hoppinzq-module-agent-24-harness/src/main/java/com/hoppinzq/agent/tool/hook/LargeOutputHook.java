package com.hoppinzq.agent.tool.hook;

/**
 * 内置钩子 3：PostToolUse 输出截断。
 * <p>
 * 工具输出过长会撑爆上下文窗口。这条钩子在结果回灌给模型前做硬截断：
 * 超过 {@link #MAX} 字符就保留头部 + 尾部，中间用占位符替代。
 *
 * <p>由于 PostToolUse 阶段的 ToolResult 是"已经被回灌的字符串"，
 * 本钩子仅打印警告（避免破坏 ToolResult 不可变性）；真实工程中可由调用方读取返回值替换之。
 *
 * @author hoppinzq
 */
public class LargeOutputHook implements HookCallback {

    public static final int MAX = 4000;

    @Override
    public String apply(HookContext ctx) {
        if (ctx.getToolResult() == null) {
            return null;
        }
        if (ctx.getToolResult().length() <= MAX) {
            return null;
        }
        // 仅打印提示；返回 null 表示不阻断。生产实现可返回截断后的字符串作为新结果。
        int dropped = ctx.getToolResult().length() - MAX;
        System.out.printf("\u001b[93m[LargeOutputHook]\u001b[0m %s 输出过长（%d 字符，建议截断 %d）%n",
                ctx.getToolName(), ctx.getToolResult().length(), dropped);
        return null;
    }
}
