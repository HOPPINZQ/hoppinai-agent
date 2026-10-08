package com.hoppinzq.agent.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.session.TokenUsage;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * {@link LlmProvider} 的 Anthropic 协议实现（com.anthropic:anthropic-java）。
 * <p>适用于 Anthropic 官方及 Anthropic 兼容端点（DeepSeek / 代理等）。
 * <ul>
 *   <li>中立 {@code role=tool} 消息在发送前合并为一条 user 消息内嵌 tool_result 块（Anthropic 协议形态）</li>
 *   <li>工具入参由 SDK 的 {@code JsonValue} 转为 JSON 字符串表示</li>
 *   <li>countTokens 走原生 count-tokens 接口（精确值）</li>
 *   <li>流式暂未实现（接口默认抛 {@link UnsupportedOperationException}）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AnthropicProvider implements LlmProvider {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AnthropicClient client;

    public AnthropicProvider(String baseUrl, String apiKey, Duration timeout, int maxRetries) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
    }

    // ============================== 阻塞式 ==============================

    @Override
    public LlmResponse complete(LlmRequest request) {
        Message message = client.messages().create(toParams(request));
        return LlmResponse.builder()
                .message(toNeutralMessage(message))
                .finishReason(mapFinishReason(message))
                .usage(toTokenUsage(message.usage()))
                .build();
    }

    // ============================== 流式 ==============================

    /**
     * Anthropic 原生流式（RawMessageStreamEvent）→ 中立 {@link StreamListener}：
     * 文本增量实时回调；工具调用块结束时以累积后的完整入参 JSON 整块回调。
     */
    @Override
    public void stream(LlmRequest request, StreamListener listener) {
        try (com.anthropic.core.http.StreamResponse<com.anthropic.models.messages.RawMessageStreamEvent> streamResponse =
                     client.messages().createStreaming(toParams(request))) {
            listener.onStart();
            // 工具调用块累积状态（anthropic 的入参以 partial_json 分片到达）
            boolean inToolBlock = false;
            String toolId = null;
            String toolName = null;
            StringBuilder toolArgs = new StringBuilder();
            LlmResponse.FinishReason finishReason = LlmResponse.FinishReason.STOP;
            long inputTokens = 0;
            long outputTokens = 0;

            java.util.Iterator<com.anthropic.models.messages.RawMessageStreamEvent> it = streamResponse.stream().iterator();
            while (it.hasNext()) {
                com.anthropic.models.messages.RawMessageStreamEvent event = it.next();
                if (event.isMessageStart()) {
                    Usage usage = event.asMessageStart().message().usage();
                    if (usage != null) {
                        inputTokens = usage.inputTokens();
                    }
                } else if (event.isContentBlockStart()) {
                    com.anthropic.models.messages.RawContentBlockStartEvent blockStart = event.asContentBlockStart();
                    if (blockStart.contentBlock().isToolUse()) {
                        inToolBlock = true;
                        com.anthropic.models.messages.ToolUseBlock toolUse = blockStart.contentBlock().asToolUse();
                        toolId = toolUse.id();
                        toolName = toolUse.name();
                        toolArgs.setLength(0);
                    }
                } else if (event.isContentBlockDelta()) {
                    com.anthropic.models.messages.RawContentBlockDeltaEvent delta = event.asContentBlockDelta();
                    if (delta.delta().isText()) {
                        String text = delta.delta().asText().text();
                        if (text != null && !text.isEmpty()) {
                            listener.onTextDelta(text);
                        }
                    } else if (delta.delta().isInputJson()) {
                        toolArgs.append(delta.delta().asInputJson().partialJson());
                    }
                } else if (event.isContentBlockStop()) {
                    if (inToolBlock) {
                        listener.onToolUse(toolId, toolName,
                                toolArgs.length() == 0 ? "{}" : toolArgs.toString());
                        inToolBlock = false;
                    }
                } else if (event.isMessageDelta()) {
                    com.anthropic.models.messages.RawMessageDeltaEvent deltaEvent = event.asMessageDelta();
                    if (deltaEvent.usage() != null) {
                        outputTokens = deltaEvent.usage().outputTokens();
                    }
                    com.anthropic.models.messages.StopReason reason = deltaEvent.delta().stopReason().orElse(null);
                    if (com.anthropic.models.messages.StopReason.TOOL_USE.equals(reason)) {
                        finishReason = LlmResponse.FinishReason.TOOL_CALLS;
                    } else if (com.anthropic.models.messages.StopReason.MAX_TOKENS.equals(reason)) {
                        finishReason = LlmResponse.FinishReason.LENGTH;
                    } else if (reason != null) {
                        finishReason = LlmResponse.FinishReason.STOP;
                    }
                }
            }
            TokenUsage usage = inputTokens == 0 && outputTokens == 0 ? null : TokenUsage.builder()
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .timestamp(java.time.LocalDateTime.now().format(TS_FMT))
                    .build();
            listener.onComplete(finishReason, usage);
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    // ============================== token 计数 ==============================

    @Override
    public int countTokens(LlmRequest request) {
        MessageCountTokensParams.Builder builder = MessageCountTokensParams.builder()
                .model(request.getModel())
                .messages(toMessageParams(request.getMessages()));
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            builder.system(request.getSystemPrompt());
        }
        return (int) client.messages().countTokens(builder.build()).inputTokens();
    }

    // ============================== 中立 -> Anthropic ==============================

    private MessageCreateParams toParams(LlmRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(request.getModel())
                .messages(toMessageParams(request.getMessages()))
                .maxTokens(request.getMaxTokens());
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            builder.system(request.getSystemPrompt());
        }
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        for (ToolDefinition tool : request.getTools()) {
            builder.addTool(ToolUnion.ofTool(Tool.builder()
                    .name(tool.getName())
                    .description(tool.getDescription())
                    .inputSchema(toInputSchema(tool.getInputSchema()))
                    .build()));
        }
        return builder.build();
    }

    /**
     * 中立消息列表 → Anthropic 消息列表。
     * 连续的 {@code role=tool} 消息合并为一条 user 消息内嵌 tool_result 块。
     */
    private List<MessageParam> toMessageParams(List<LlmMessage> messages) {
        List<MessageParam> out = new ArrayList<>();
        List<LlmMessage> toolBuffer = new ArrayList<>();
        for (LlmMessage m : messages) {
            if (m.isTool()) {
                toolBuffer.add(m);
                continue;
            }
            flushToolBuffer(out, toolBuffer);
            if (m.isAssistant()) {
                out.add(toAssistantParam(m));
            } else {
                out.add(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(m.getText() == null ? "" : m.getText())
                        .build());
            }
        }
        flushToolBuffer(out, toolBuffer);
        return out;
    }

    private void flushToolBuffer(List<MessageParam> out, List<LlmMessage> toolBuffer) {
        if (toolBuffer.isEmpty()) {
            return;
        }
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (LlmMessage m : toolBuffer) {
            blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(m.getToolCallId())
                    .content(m.getText() == null ? "" : m.getText())
                    .build()));
        }
        out.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(MessageParam.Content.ofBlockParams(blocks))
                .build());
        toolBuffer.clear();
    }

    private MessageParam toAssistantParam(LlmMessage m) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (m.getText() != null && !m.getText().isBlank()) {
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.getText()).build()));
        }
        for (LlmMessage.ToolCall tc : m.getToolCalls()) {
            blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(tc.getId())
                    .name(tc.getName())
                    .input(jsonToInput(tc.getArgumentsJson()))
                    .build()));
        }
        if (blocks.isEmpty()) {
            // 防御：Anthropic 不接受空 content
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text("").build()));
        }
        return MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(MessageParam.Content.ofBlockParams(blocks))
                .build();
    }

    private Tool.InputSchema toInputSchema(ObjectNode schema) {
        Tool.InputSchema.Builder builder = Tool.InputSchema.builder();
        JsonNode properties = schema.get("properties");
        if (properties != null) {
            builder.properties(JsonValue.fromJsonNode(properties));
        }
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            List<String> list = new ArrayList<>();
            required.forEach(n -> list.add(n.asText()));
            builder.required(list);
        }
        return builder.build();
    }

    /**
     * 工具入参 JSON 字符串 → SDK 的 ToolUseBlockParam.Input（内部为 additionalProperties）。
     */
    private ToolUseBlockParam.Input jsonToInput(String argumentsJson) {
        ToolUseBlockParam.Input.Builder builder = ToolUseBlockParam.Input.builder();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            if (node.isObject()) {
                node.fields().forEachRemaining(e ->
                        builder.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue())));
            }
        } catch (Exception ignore) {
            // 解析失败按空入参处理，让模型在下一轮看到工具报错后自行纠正
        }
        return builder.build();
    }

    // ============================== Anthropic -> 中立 ==============================

    private LlmMessage toNeutralMessage(Message message) {
        StringBuilder text = new StringBuilder();
        List<LlmMessage.ToolCall> calls = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            if (block.isText()) {
                block.text().map(com.anthropic.models.messages.TextBlock::text).filter(t -> !t.isBlank())
                        .ifPresent(t -> text.append(text.length() > 0 ? "\n" : "").append(t));
            } else if (block.isToolUse()) {
                ToolUseBlock toolUse = block.asToolUse();
                calls.add(LlmMessage.ToolCall.builder()
                        .id(toolUse.id())
                        .name(toolUse.name())
                        .argumentsJson(inputToJson(toolUse))
                        .build());
            }
        }
        return LlmMessage.assistant(text.length() > 0 ? text.toString() : null, calls);
    }

    private String inputToJson(ToolUseBlock toolUse) {
        try {
            JsonNode node = toolUse._input().convert(JsonNode.class);
            return node == null ? "{}" : node.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private LlmResponse.FinishReason mapFinishReason(Message message) {
        return message.stopReason()
                .map(reason -> {
                    if (com.anthropic.models.messages.StopReason.TOOL_USE.equals(reason)) {
                        return LlmResponse.FinishReason.TOOL_CALLS;
                    }
                    if (com.anthropic.models.messages.StopReason.MAX_TOKENS.equals(reason)) {
                        return LlmResponse.FinishReason.LENGTH;
                    }
                    return LlmResponse.FinishReason.STOP;
                })
                .orElse(LlmResponse.FinishReason.STOP);
    }

    private TokenUsage toTokenUsage(Usage usage) {
        if (usage == null) {
            return null;
        }
        return TokenUsage.builder()
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .cacheReadTokens(usage.cacheReadInputTokens().orElse(null))
                .cacheCreationTokens(usage.cacheCreationInputTokens().orElse(null))
                .timestamp(LocalDateTime.now().format(TS_FMT))
                .build();
    }
}
