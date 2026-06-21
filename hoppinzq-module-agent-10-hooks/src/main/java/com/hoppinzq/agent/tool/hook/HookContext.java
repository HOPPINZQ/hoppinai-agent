package com.hoppinzq.agent.tool.hook;

import lombok.Builder;
import lombok.Data;

/**
 * 钩子上下文。不同事件会填充不同字段：
 * <ul>
 *   <li>{@link HookEvent#USER_PROMPT_SUBMIT}：仅 userInput 非空</li>
 *   <li>{@link HookEvent#PRE_TOOL_USE} / {@link HookEvent#POST_TOOL_USE}：toolName、toolInput 非空；
 *       POST 时 toolResult 也非空</li>
 *   <li>{@link HookEvent#STOP}：所有字段可能为空（只是通知"一轮结束"）</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@Builder
public class HookContext {
    /** 用户本轮输入的 prompt（仅 USER_PROMPT_SUBMIT 有值） */
    private String userInput;
    /** 即将/已执行的工具名 */
    private String toolName;
    /** 工具入参 JSON 字符串 */
    private String toolInput;
    /** 工具返回结果（POST_TOOL_USE 才有） */
    private String toolResult;
    /** 工具是否报错（POST_TOOL_USE 才有意义） */
    private boolean toolError;

    public static HookContext forPrompt(String userInput) {
        return HookContext.builder().userInput(userInput).build();
    }

    public static HookContext forPreTool(String toolName, String toolInput) {
        return HookContext.builder().toolName(toolName).toolInput(toolInput).build();
    }

    public static HookContext forPostTool(String toolName, String toolInput, String toolResult, boolean error) {
        return HookContext.builder()
                .toolName(toolName).toolInput(toolInput)
                .toolResult(toolResult).toolError(error).build();
    }

    public static HookContext forStop() {
        return HookContext.builder().build();
    }
}
