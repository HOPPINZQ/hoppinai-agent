package com.hoppinzq.agent.tool.hook;

/**
 * 钩子回调。
 * <p>
 * 规则：返回 null 表示"放行 / 无副作用"；返回非 null 字符串表示
 * <ul>
 *   <li>{@link HookEvent#USER_PROMPT_SUBMIT}：用返回值替换原 prompt（实现改写）</li>
 *   <li>{@link HookEvent#PRE_TOOL_USE}：用返回值作为 isError=false 的 ToolResult 直接回灌给模型，跳过执行（实现阻断）</li>
 *   <li>其它事件：返回值仅用于副作用/日志，不影响主流程</li>
 * </ul>
 *
 * @author hoppinzq
 */
@FunctionalInterface
public interface HookCallback {
    String apply(HookContext ctx);
}
