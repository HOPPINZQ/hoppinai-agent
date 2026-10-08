package com.hoppinzq.agent.session;

import com.hoppinzq.agent.client.LlmMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 在协议中立的 {@link LlmMessage} 与可序列化 {@link SessionMessage} 之间双向转换。
 * <p>该类无状态、线程不安全（仅设计用于单线程 agent 循环），且不依赖任何 SDK。
 * <p>落盘格式与协议无关：tool 结果统一存为 user 角色消息内的 {@code tool_result} 块
 * （中立模型要求每条工具结果是独立的 {@code role=tool} 消息，加载时由
 * {@link #toLlmMessages(SessionMessage)} 展开还原）。
 *
 * @author hoppinzq
 */
public final class MessageConverter {

    // ============================== 中立消息 -> SessionMessage ==============================

    public SessionMessage toSessionMessage(LlmMessage m) {
        if (m.isAssistant()) {
            return toSessionMessageFromAssistant(m);
        }
        if (m.isTool()) {
            return toMergedSessionMessage(List.of(m));
        }
        // 多模态 user 消息：落盘时图片降级为描述文本（会话文件格式保持不变，历史兼容）
        if (m.getParts() != null && !m.getParts().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (LlmMessage.ContentPart part : m.getParts()) {
                if ("image".equals(part.getType())) {
                    sb.append("[图片: ").append(part.getMediaType() == null ? "image/*" : part.getMediaType())
                      .append(" base64, ").append(part.getData() == null ? 0 : part.getData().length())
                      .append(" chars]\n");
                } else if ("file".equals(part.getType())) {
                    sb.append("[文件: ").append(part.getFilename()).append("]\n").append(part.getText()).append('\n');
                } else {
                    sb.append(part.getText() == null ? "" : part.getText()).append('\n');
                }
            }
            return SessionMessage.builder().role("user").text(sb.toString()).build();
        }
        return SessionMessage.builder().role("user").text(m.getText() == null ? "" : m.getText()).build();
    }

    private SessionMessage toSessionMessageFromAssistant(LlmMessage m) {
        SessionMessage.SessionMessageBuilder b = SessionMessage.builder().role("assistant");
        // DeepSeek 等后端返回 tool_calls 时 text 常为 null，blank 文本不落盘
        if (m.getText() != null && !m.getText().isBlank()) {
            b.text(m.getText());
        }
        List<SessionBlock> blocks = new ArrayList<>();
        for (LlmMessage.ToolCall call : m.getToolCalls()) {
            blocks.add(SessionBlock.builder()
                    .type("tool_use")
                    .toolUseId(call.getId())
                    .toolName(call.getName())
                    .toolInputJson(call.getArgumentsJson())
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
     * <p>中立模型没有 isError 标记，统一记为 {@code false}；错误信息以文本前缀表达。
     */
    public SessionMessage toMergedSessionMessage(List<LlmMessage> toolMessages) {
        List<SessionBlock> blocks = new ArrayList<>();
        for (LlmMessage m : toolMessages) {
            blocks.add(SessionBlock.builder()
                    .type("tool_result")
                    .toolUseId(m.getToolCallId())
                    .toolResultContent(m.getText() == null ? "" : m.getText())
                    .isError(Boolean.FALSE)
                    .build());
        }
        return SessionMessage.builder().role("user").blocks(blocks).build();
    }

    // ============================== SessionMessage -> 中立消息 ==============================

    /**
     * 反向转换。一条 SessionMessage 可能对应多条中立消息：
     * user+tool_result 块展开为 N 条 {@code role=tool} 消息（每个 tool_call_id 恰好一条应答）。
     */
    public List<LlmMessage> toLlmMessages(SessionMessage sm) {
        List<LlmMessage> out = new ArrayList<>();
        boolean assistant = "assistant".equalsIgnoreCase(sm.getRole() == null ? "user" : sm.getRole());
        if (assistant) {
            out.add(toAssistant(sm));
            return out;
        }
        // user 角色：tool_result 块展开为 N 条 tool 消息；text 块/纯文本为 1 条 user 消息
        boolean hasToolResult = false;
        StringBuilder userText = null;
        if (sm.getBlocks() != null) {
            for (SessionBlock sb : sm.getBlocks()) {
                if ("tool_result".equals(sb.getType())) {
                    hasToolResult = true;
                    out.add(LlmMessage.tool(sb.getToolUseId(),
                            sb.getToolResultContent() == null ? "" : sb.getToolResultContent()));
                } else if ("text".equals(sb.getType()) && sb.getText() != null && !sb.getText().isBlank()) {
                    userText = userText == null ? new StringBuilder(sb.getText()) : userText.append('\n').append(sb.getText());
                }
            }
        }
        if (userText != null) {
            out.add(LlmMessage.user(userText.toString()));
            return out;
        }
        if (hasToolResult) {
            return out;
        }
        // 兜底：空内容
        out.add(LlmMessage.user(sm.getText() == null ? "" : sm.getText()));
        return out;
    }

    private LlmMessage toAssistant(SessionMessage sm) {
        String text = sm.getText() != null && !sm.getText().isBlank() ? sm.getText() : null;
        List<LlmMessage.ToolCall> calls = new ArrayList<>();
        StringBuilder blockText = null;
        if (sm.getBlocks() != null) {
            for (SessionBlock sb : sm.getBlocks()) {
                if ("tool_use".equals(sb.getType())) {
                    calls.add(LlmMessage.ToolCall.builder()
                            .id(sb.getToolUseId())
                            .name(sb.getToolName())
                            .argumentsJson(sb.getToolInputJson() == null || sb.getToolInputJson().isBlank()
                                    ? "{}" : sb.getToolInputJson())
                            .build());
                } else if ("text".equals(sb.getType()) && sb.getText() != null && !sb.getText().isBlank()) {
                    blockText = blockText == null ? new StringBuilder(sb.getText()) : blockText.append('\n').append(sb.getText());
                }
            }
        }
        if (text == null && blockText != null) {
            text = blockText.toString();
        }
        // text 为 null 不设置：assistant 仅带 tool_calls、无文本是合法且常见的
        return LlmMessage.assistant(text, calls);
    }
}
