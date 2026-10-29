package com.hoppinzq.agent.tool.hook;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 钩子注册中心。
 * <p>
 * 内部结构 {@code Map<HookEvent, List<HookCallback>>}，提供：
 * <ul>
 *   <li>{@link #register(HookEvent, HookCallback)} — 按事件追加回调</li>
 *   <li>{@link #trigger(HookEvent, HookContext)} — 顺序触发，返回首个非 null 结果；
 *       若所有回调都返回 null，则返回 null（表示放行）</li>
 * </ul>
 *
 * <p>trigger 的"首个非 null 即短路"语义对 {@link HookEvent#PRE_TOOL_USE} 至关重要：
 * 只要有一个钩子返回非 null，工具就会被跳过，返回值作为 ToolResult 回灌。
 *
 * @author hoppinzq
 */
public class HookRegistry {

    private final Map<HookEvent, List<HookCallback>> hooks = new EnumMap<>(HookEvent.class);

    public HookRegistry() {
        for (HookEvent e : HookEvent.values()) {
            hooks.put(e, new ArrayList<>());
        }
    }

    public void register(HookEvent event, HookCallback callback) {
        hooks.get(event).add(callback);
    }

    /**
     * 触发事件下所有回调；返回首个非 null 的结果；若全部返回 null，返回 null。
     */
    public String trigger(HookEvent event, HookContext ctx) {
        String firstNonNull = null;
        for (HookCallback cb : hooks.get(event)) {
            String r;
            try {
                r = cb.apply(ctx);
            } catch (Exception e) {
                System.out.printf("\u001b[91m[hook 异常]\u001b[0m %s: %s%n", event, e.getMessage());
                continue;
            }
            if (r != null && firstNonNull == null) {
                firstNonNull = r;
                // 不 break —— 让后续钩子的副作用（日志、统计）仍能跑
            }
        }
        return firstNonNull;
    }
}
