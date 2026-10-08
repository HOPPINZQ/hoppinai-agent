package com.hoppinzq.agent.session;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 SDK 的 {@link ChatCompletionMessageParam} 与可序列化 {@link SessionMessage} 之间双向转换。
 * <p>该类无状态、线程不安全（仅设计用于单线程 agent 循环）。
 * <p>落盘格式与协议无关：tool 结果统一存为 user 角色消息内的 {@code tool_result} 块
 * （OpenAI 协议要求每条 tool 结果是独立的 {@code role=tool} 消息，加载时由
 * {@link #toMessageParams(SessionMessage)} 展开还原）。
 *
 * @author hoppinzq
 */
public final class MessageConverter {

    // ============================== OpenAI 消息 -> SessionMessage ==============================

    public SessionMessage toSessionMessage(ChatCompletionMessageParam param) {
        if (param.isAssistant()) {
            return toSessionMessage(param.asAssistant());
        }
        if (param.isTool()) {
            return toMergedSessionMessage(List.of(param));
        }
        return toSessionMessage(param.asUser());
    }

    public SessionMessage toSessionMessage(ChatCompletionUserMessageParam up) {
        String text = up.content().isText() ? up.content().asText() : "";
        return SessionMessage.builder().role("user").text(text).build();
    }

    public SessionMessage toSessionMessage(ChatCompletionAssistantMessageParam ap) {
        SessionMessage.SessionMessageBuilder b = SessionMessage.builder().role("assistant");
        // DeepSeek 等后端返回 tool_calls 时 content 常为 null，blank 文本不落盘
        ap.content().filter(ChatCompletionAssistantMessageParam.Content::isText)
                .map(ChatCompletionAssistantMessageParam.Content::asText)
                .filter(t -> !t.isBlank())
                .ifPresent(b::text);
        List<SessionBlock> blocks = new ArrayList<>();
        for (ChatCompletionMessageToolCall call : ap.toolCalls().orElse(List.of())) {
            if (!call.isFunction()) {
                continue;
            }
            ChatCompletionMessageFunctionToolCall fn = call.asFunction();
            blocks.add(SessionBlock.builder()
                    .type("tool_use")
                    .toolUseId(fn.id())
                    .toolName(fn.function().name())
                    .toolInputJson(fn.function().arguments())
                    .build());
        }
        if (!blocks.isEmpty()) {
            b.blocks(blocks);
        }
        return b.build();
    }

    /**
     * 把一轮的 N 条 {@code role=tool} 消息合并为一条 user 角色 SessionMessage
     * （内含 N 个 {@code tool_result} 块），保持与旧版一致的落盘格式。
     * <p>OpenAI 协议没有 isError 标记，统一记为 {@code false}；错误信息以文本前缀表达。
     */
    public SessionMessage toMergedSessionMessage(List<ChatCompletionMessageParam> toolParams) {
        List<SessionBlock> blocks = new ArrayList<>();
        for (ChatCompletionMessageParam p : toolParams) {
            ChatCompletionToolMessageParam tp = p.asTool();
            blocks.add(SessionBlock.builder()
                    .type("tool_result")
                    .toolUseId(tp.toolCallId())
                    .toolResultContent(tp.content().isText() ? tp.content().asText() : "")
                    .isError(Boolean.FALSE)
                    .build());
        }
        return SessionMessage.builder().role("user").blocks(blocks).build();
    }

    // ============================== SessionMessage -> OpenAI 消息 ==============================

    /**
     * 反向转换。一条 SessionMessage 可能对应多条 OpenAI 消息：
     * user+tool_result 块展开为 N 条 {@code role=tool} 消息（协议要求每个 tool_call_id 恰好一条应答）。
     */
    public List<ChatCompletionMessageParam> toMessageParams(SessionMessage sm) {
        List<ChatCompletionMessageParam> out = new ArrayList<>();
        boolean assistant = "assistant".equalsIgnoreCase(sm.getRole() == null ? "user" : sm.getRole());
        if (assistant) {
            out.add(ChatCompletionMessageParam.ofAssistant(buildAssistant(sm)));
            return out;
        }
        // user 角色：tool_result 块展开为 N 条 tool 消息；text 块/纯文本为 1 条 user 消息
        boolean hasToolResult = false;
        StringBuilder userText = null;
        if (sm.getBlocks() != null) {
            for (SessionBlock sb : sm.getBlocks()) {
                if ("tool_result".equals(sb.getType())) {
                    hasToolResult = true;
                    out.add(ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
                            .toolCallId(sb.getToolUseId())
                            .content(sb.getToolResultContent() == null ? "" : sb.getToolResultContent())
                            .build()));
                } else if ("text".equals(sb.getType()) && sb.getText() != null && !sb.getText().isBlank()) {
                    userText = userText == null ? new StringBuilder(sb.getText()) : userText.append('\n').append(sb.getText());
                }
            }
        }
        if (hasToolResult) {
            if (userText != null) {
                out.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                        .content(userText.toString()).build()));
            }
            return out;
        }
        if (userText != null) {
            out.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                    .content(userText.toString()).build()));
            return out;
        }
        // 兜底：空内容
        out.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                .content(sm.getText() == null ? "" : sm.getText()).build()));
        return out;
    }

    private ChatCompletionAssistantMessageParam buildAssistant(SessionMessage sm) {
        ChatCompletionAssistantMessageParam.Builder b = ChatCompletionAssistantMessageParam.builder();
        String topText = sm.getText() != null && !sm.getText().isBlank() ? sm.getText() : null;
        List<ChatCompletionMessageToolCall> calls = new ArrayList<>();
        StringBuilder blockText = null;
        if (sm.getBlocks() != null) {
            for (SessionBlock sb : sm.getBlocks()) {
                if ("tool_use".equals(sb.getType())) {
                    calls.add(ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
                            .id(sb.getToolUseId())
                            .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                    .name(sb.getToolName())
                                    .arguments(sb.getToolInputJson() == null || sb.getToolInputJson().isBlank()
                                            ? "{}" : sb.getToolInputJson())
                                    .build())
                            .build()));
                } else if ("text".equals(sb.getType()) && sb.getText() != null && !sb.getText().isBlank()) {
                    blockText = blockText == null ? new StringBuilder(sb.getText()) : blockText.append('\n').append(sb.getText());
                }
            }
        }
        if (topText != null) {
            b.content(topText);
        } else if (blockText != null) {
            b.content(blockText.toString());
        }
        // content 为 null 不设置：assistant 仅带 tool_calls、无文本是合法且常见的
        if (!calls.isEmpty()) {
            b.toolCalls(calls);
        }
        return b.build();
    }
}
