package com.hoppinzq.agent.session;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * 在 SDK 的 {@link MessageParam} 与可序列化 {@link SessionMessage} 之间双向转换。
 * <p>该类无状态、线程不安全（仅设计用于单线程 agent 循环）。
 *
 * @author hoppinzq
 */
public final class MessageConverter {

    private final ObjectMapper mapper;

    public MessageConverter() {
        this(OBJECT_MAPPER);
    }

    public MessageConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // ============================== MessageParam -> SessionMessage ==============================

    public SessionMessage toSessionMessage(MessageParam param) {
        SessionMessage.SessionMessageBuilder b = SessionMessage.builder()
                .role(roleString(param));
        MessageParam.Content content = param.content();
        if (content.isString()) {
            b.text(content.asString());
        } else if (content.isBlockParams()) {
            List<SessionBlock> blocks = new ArrayList<>();
            for (ContentBlockParam cbp : content.asBlockParams()) {
                blocks.add(toSessionBlock(cbp));
            }
            b.blocks(blocks);
        }
        return b.build();
    }

    private SessionBlock toSessionBlock(ContentBlockParam cbp) {
        if (cbp.isText()) {
            TextBlockParam t = cbp.asText();
            return SessionBlock.builder().type("text").text(t.text()).build();
        }
        if (cbp.isToolUse()) {
            ToolUseBlockParam tu = cbp.asToolUse();
            return SessionBlock.builder()
                    .type("tool_use")
                    .toolUseId(tu.id())
                    .toolName(tu.name())
                    .toolInputJson(inputToJsonString(tu))
                    .build();
        }
        if (cbp.isToolResult()) {
            ToolResultBlockParam tr = cbp.asToolResult();
            SessionBlock.SessionBlockBuilder b = SessionBlock.builder()
                    .type("tool_result")
                    .toolUseId(tr.toolUseId());
            Optional<String> str = tr.content().flatMap(ToolResultBlockParam.Content::string);
            str.ifPresent(b::toolResultContent);
            tr.isError().ifPresent(b::isError);
            return b.build();
        }
        // 其他块类型（image/thinking/...）退化为文本占位，避免丢失消息结构
        return SessionBlock.builder().type("text").text("[unsupported block]").build();
    }

    /** 提取 ToolUseBlockParam 的 input 为 JSON 字符串。 */
    private String inputToJsonString(ToolUseBlockParam tu) {
        try {
            // SDK 的 input() 返回 Input 对象，其底层为 additionalProperties Map<String, JsonValue>
            Map<String, JsonValue> map = tu.input()._additionalProperties();
            if (map == null || map.isEmpty()) {
                return "{}";
            }
            // 把 Map<String, JsonValue> 转为 ObjectNode；JsonValue 通过 SDK 自带的 convert 落到 JsonNode
            com.fasterxml.jackson.databind.node.ObjectNode root = mapper.createObjectNode();
            for (Map.Entry<String, JsonValue> e : map.entrySet()) {
                JsonNode child = e.getValue().convert(JsonNode.class);
                root.set(e.getKey(), child == null ? mapper.nullNode() : child);
            }
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    // ============================== SessionMessage -> MessageParam ==============================

    public MessageParam toMessageParam(SessionMessage sm) {
        MessageParam.Builder b = MessageParam.builder()
                .role(parseRole(sm.getRole()));
        if (sm.getText() != null) {
            b.content(sm.getText());
        } else if (sm.getBlocks() != null) {
            List<ContentBlockParam> cbps = new ArrayList<>();
            for (SessionBlock sb : sm.getBlocks()) {
                cbps.add(toContentBlockParam(sb));
            }
            b.content(MessageParam.Content.ofBlockParams(cbps));
        } else {
            // 兜底：空内容
            b.content("");
        }
        return b.build();
    }

    private ContentBlockParam toContentBlockParam(SessionBlock sb) {
        String type = sb.getType() == null ? "text" : sb.getType();
        switch (type) {
            case "tool_use":
                return ContentBlockParam.ofToolUse(buildToolUse(sb));
            case "tool_result":
                return ContentBlockParam.ofToolResult(buildToolResult(sb));
            case "text":
            default:
                return ContentBlockParam.ofText(
                        TextBlockParam.builder().text(sb.getText() == null ? "" : sb.getText()).build());
        }
    }

    private ToolUseBlockParam buildToolUse(SessionBlock sb) {
        ToolUseBlockParam.Builder b = ToolUseBlockParam.builder()
                .id(sb.getToolUseId())
                .name(sb.getToolName());
        ToolUseBlockParam.Input.Builder ib = ToolUseBlockParam.Input.builder();
        Map<String, JsonValue> map = parseInputMap(sb.getToolInputJson());
        if (!map.isEmpty()) {
            ib.putAllAdditionalProperties(map);
        }
        b.input(ib.build());
        return b.build();
    }

    private ToolResultBlockParam buildToolResult(SessionBlock sb) {
        ToolResultBlockParam.Builder b = ToolResultBlockParam.builder()
                .toolUseId(sb.getToolUseId());
        if (sb.getToolResultContent() != null) {
            b.content(sb.getToolResultContent());
        }
        if (sb.getIsError() != null) {
            b.isError(sb.getIsError());
        }
        return b.build();
    }

    /** 将 JSON 字符串解析为 {@code Map<String, JsonValue>}。 */
    private Map<String, JsonValue> parseInputMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = mapper.readTree(json);
            if (!node.isObject()) {
                return Map.of();
            }
            java.util.HashMap<String, JsonValue> out = new java.util.HashMap<>();
            node.fields().forEachRemaining(e -> out.put(e.getKey(), JsonValue.fromJsonNode(e.getValue())));
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private MessageParam.Role parseRole(String role) {
        if (role == null) {
            return MessageParam.Role.USER;
        }
        String r = role.toLowerCase();
        if ("assistant".equals(r)) {
            return MessageParam.Role.ASSISTANT;
        }
        return MessageParam.Role.USER;
    }

    /**
     * 安全读取 MessageParam 的 role 字符串。
     * <p>SDK 的 {@code param.role()} 会做严格校验，对来自 {@code message.toParam()} 的
     * 「未知但合法」消息会抛 {@code AnthropicInvalidDataException}。
     * 这里改走底层 {@code _role()} 的 JsonField，直接拿原始字符串。
     */
    private String roleString(MessageParam param) {
        return param._role().asString().orElse("user");
    }
}
