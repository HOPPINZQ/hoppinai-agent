package com.hoppinzq.agent.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 协议中立的一条聊天消息。
 * <p>三种角色，对应两种协议（Anthropic / OpenAI）的公共子集：
 * <ul>
 *   <li>{@code user} —— 用户输入，使用 {@link #text}</li>
 *   <li>{@code assistant} —— 模型回复：{@link #text}（可为空）+ {@link #toolCalls}</li>
 *   <li>{@code tool} —— 单条工具结果：{@link #toolCallId} + {@link #text}（OpenAI 协议要求每个 tool_call_id 恰好一条）</li>
 * </ul>
 * 工具入参统一用原始 JSON 字符串（{@link ToolCall#argumentsJson}），由 Provider 负责与各 SDK 类型互转。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmMessage {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    /** 角色：user / assistant / tool */
    private String role;
    /** 文本内容：user 输入 / assistant 回复 / tool 结果内容 */
    private String text;
    /** role=tool 时的工具调用 ID */
    private String toolCallId;
    /** role=assistant 时的工具调用列表 */
    @Builder.Default
    private List<ToolCall> toolCalls = new ArrayList<>();
    /** 多模态内容块（仅 user 消息使用；非空时优先于 text，由 Provider 映射为各自协议的图片/文本块） */
    private List<ContentPart> parts;

    public static LlmMessage user(String text) {
        return LlmMessage.builder().role(ROLE_USER).text(text).build();
    }

    public static LlmMessage assistant(String text, List<ToolCall> toolCalls) {
        return LlmMessage.builder().role(ROLE_ASSISTANT).text(text).toolCalls(toolCalls).build();
    }

    public static LlmMessage tool(String toolCallId, String content) {
        return LlmMessage.builder().role(ROLE_TOOL).toolCallId(toolCallId).text(content).build();
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    public boolean isAssistant() {
        return ROLE_ASSISTANT.equals(role);
    }

    public boolean isTool() {
        return ROLE_TOOL.equals(role);
    }

    /**
     * 多模态内容块：text / image(base64) / file。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContentPart {
        private String type;      // "text" / "image" / "file"
        private String text;      // text/file 的文本内容
        private String mediaType; // image 的 MIME 类型，如 image/png
        private String data;      // image 的 base64 数据
        private String filename;  // file 的文件名
    }

    /**
     * 一次工具调用：ID + 工具名 + 原始 JSON 字符串入参。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ToolCall {
        private String id;
        private String name;
        /** 原始 JSON 字符串形式的入参（各协议的公共表示） */
        private String argumentsJson;
    }
}
