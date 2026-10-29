package com.hoppinzq.agent.tool.permission;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 权限检查决策结果。
 * <p>
 * 三种结果对应三重闸门：
 * <ul>
 *   <li>{@link Type#ALLOW} — 放行，正常执行工具</li>
 *   <li>{@link Type#DENY}  — 拒绝，把拒绝原因作为 isError=true 的 ToolResult 回灌给模型</li>
 *   <li>{@link Type#ASK}   — 交互式确认，由 Scanner 读用户输入</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
public class Decision {

    public enum Type {
        /** 放行 */
        ALLOW,
        /** 直接拒绝 */
        DENY,
        /** 需用户确认 */
        ASK
    }

    private final Type type;
    /** 拒绝原因或确认提示语；ALLOW 时为 null */
    private final String reason;

    public static Decision allow() {
        return new Decision(Type.ALLOW, null);
    }

    public static Decision deny(String reason) {
        return new Decision(Type.DENY, reason);
    }

    public static Decision ask(String prompt) {
        return new Decision(Type.ASK, prompt);
    }
}
