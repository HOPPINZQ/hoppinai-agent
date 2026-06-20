package com.hoppinzq.agent.tool.hook;

/**
 * 钩子事件枚举（4 个生命周期点）。
 * <p>
 * 对应 Python 教程 s04_hooks 中的 4 个事件点：
 * <ul>
 *   <li>{@link #USER_PROMPT_SUBMIT} — 用户输入后、LLM 调用前（可改写/拒绝输入）</li>
 *   <li>{@link #PRE_TOOL_USE} — 工具执行前；回调返回非 null 即阻断本次调用</li>
 *   <li>{@link #POST_TOOL_USE} — 工具执行后；可对结果做截断/审计</li>
 *   <li>{@link #STOP} — 一轮工具循环结束（模型本轮未再发工具请求）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public enum HookEvent {
    USER_PROMPT_SUBMIT,
    PRE_TOOL_USE,
    POST_TOOL_USE,
    STOP
}
