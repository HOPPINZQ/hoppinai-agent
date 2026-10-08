package com.hoppinzq.agent.client;

import com.hoppinzq.agent.session.TokenUsage;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link LlmProvider} 的 OpenAI 协议实现（com.openai:openai-java）。
 * <p>适用于 OpenAI 官方及一切 OpenAI 兼容端点（DeepSeek / 代理等）。
 * <ul>
 *   <li>tool 入参即原始 JSON 字符串（{@code arguments()}），无类型转换损耗</li>
 *   <li>流式需 {@code stream_options: {include_usage: true}} 才有 usage</li>
 *   <li>countTokens 无原生接口，用 jtokkit 估算（近似值）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class OpenAIProvider implements LlmProvider {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OpenAIClient client;

    public OpenAIProvider(String baseUrl, String apiKey, Duration timeout, int maxRetries) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
    }

    // ============================== 阻塞式 ==============================

    @Override
    public LlmResponse complete(LlmRequest request) {
        ChatCompletion completion = client.chat().completions().create(toParams(request, false));
        if (completion.choices().isEmpty()) {
            throw new IllegalStateException("LLM 响应中没有 choices");
        }
        ChatCompletion.Choice choice = completion.choices().get(0);
        return LlmResponse.builder()
                .message(toNeutralMessage(choice.message()))
                .finishReason(mapFinishReason(choice.finishReason()))
                .usage(toTokenUsage(completion.usage().orElse(null)))
                .build();
    }

    // ============================== 流式 ==============================

    @Override
    public void stream(LlmRequest request, StreamListener listener) {
        try (StreamResponse<ChatCompletionChunk> stream =
                     client.chat().completions().createStreaming(toParams(request, true))) {
            listener.onStart();
            // 工具调用分片按 index 累积：id/name 首片到达，arguments 逐片拼接
            Map<Long, LlmMessage.ToolCall> pendingCalls = new HashMap<>();
            LlmResponse.FinishReason finishReason = LlmResponse.FinishReason.STOP;
            TokenUsage usage = null;
            java.util.Iterator<ChatCompletionChunk> it = stream.stream().iterator();
            while (it.hasNext()) {
                ChatCompletionChunk chunk = it.next();
                if (chunk.usage().isPresent()) {
                    usage = toTokenUsage(chunk.usage().get());
                }
                if (chunk.choices().isEmpty()) {
                    continue;
                }
                ChatCompletionChunk.Choice choice = chunk.choices().get(0);
                if (choice.finishReason().isPresent()) {
                    finishReason = mapChunkFinishReason(choice.finishReason().get());
                }
                choice.delta().content().filter(c -> !c.isEmpty()).ifPresent(listener::onTextDelta);
                choice.delta().toolCalls().ifPresent(calls -> {
                    for (ChatCompletionChunk.Choice.Delta.ToolCall call : calls) {
                        LlmMessage.ToolCall acc = pendingCalls.computeIfAbsent(call.index(),
                                k -> LlmMessage.ToolCall.builder().argumentsJson("").build());
                        call.id().ifPresent(acc::setId);
                        call.function().ifPresent(fn -> {
                            fn.name().ifPresent(acc::setName);
                            fn.arguments().ifPresent(args -> acc.setArgumentsJson(acc.getArgumentsJson() + args));
                        });
                    }
                });
            }
            pendingCalls.values().stream()
                    .filter(c -> c.getId() != null && c.getName() != null)
                    .forEach(c -> listener.onToolUse(c.getId(), c.getName(), c.getArgumentsJson()));
            listener.onComplete(finishReason, usage);
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    // ============================== token 估算 ==============================

    /**
     * jtokkit(cl100k_base) 近似估算：OpenAI 协议没有原生 countTokens 端点。
     */
    @Override
    public int countTokens(LlmRequest request) {
        Encoding enc = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        int total = 4; // 每次对话的固定开销
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            total += enc.countTokens(request.getSystemPrompt());
        }
        for (LlmMessage m : request.getMessages()) {
            total += 4;
            if (m.getText() != null) {
                total += enc.countTokens(m.getText());
            }
            if (m.getToolCalls() != null) {
                for (LlmMessage.ToolCall tc : m.getToolCalls()) {
                    total += enc.countTokens(tc.getName() + tc.getArgumentsJson());
                }
            }
        }
        for (ToolDefinition tool : request.getTools()) {
            total += enc.countTokens(tool.getName() + tool.getDescription() + tool.getInputSchema().toString());
        }
        return total;
    }

    // ============================== 中立 <-> OpenAI ==============================

    private ChatCompletionCreateParams toParams(LlmRequest request, boolean streaming) {
        // messages(List) 是整体替换而非追加：system 必须拼进同一个 list
        List<ChatCompletionMessageParam> payload = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            payload.add(ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder()
                    .content(request.getSystemPrompt())
                    .build()));
        }
        for (LlmMessage m : request.getMessages()) {
            payload.add(toOpenAIMessage(m));
        }
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(request.getModel())
                .messages(payload)
                .maxTokens(request.getMaxTokens());
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (streaming) {
            builder.streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());
        }
        for (ToolDefinition tool : request.getTools()) {
            builder.addTool(ChatCompletionFunctionTool.builder()
                    .function(FunctionDefinition.builder()
                            .name(tool.getName())
                            .description(tool.getDescription())
                            .parameters(toFunctionParameters(tool.getInputSchema()))
                            .build())
                    .build());
        }
        return builder.build();
    }

    private ChatCompletionMessageParam toOpenAIMessage(LlmMessage m) {
        if (m.isAssistant()) {
            ChatCompletionAssistantMessageParam.Builder b = ChatCompletionAssistantMessageParam.builder();
            if (m.getText() != null && !m.getText().isBlank()) {
                b.content(m.getText());
            }
            // content 为 null 不设置：assistant 仅带 tool_calls、无文本是合法且常见的
            for (LlmMessage.ToolCall tc : m.getToolCalls()) {
                b.addToolCall(ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
                        .id(tc.getId())
                        .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                .name(tc.getName())
                                .arguments(tc.getArgumentsJson() == null || tc.getArgumentsJson().isBlank()
                                        ? "{}" : tc.getArgumentsJson())
                                .build())
                        .build()));
            }
            return ChatCompletionMessageParam.ofAssistant(b.build());
        }
        if (m.isTool()) {
            return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
                    .toolCallId(m.getToolCallId())
                    .content(m.getText() == null ? "" : m.getText())
                    .build());
        }
        return ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                .content(m.getText() == null ? "" : m.getText())
                .build());
    }

    private LlmMessage toNeutralMessage(com.openai.models.chat.completions.ChatCompletionMessage message) {
        String text = message.content().filter(c -> !c.isBlank()).orElse(null);
        List<LlmMessage.ToolCall> calls = new ArrayList<>();
        for (ChatCompletionMessageToolCall call : message.toolCalls().orElse(List.of())) {
            if (!call.isFunction()) {
                continue;
            }
            ChatCompletionMessageFunctionToolCall fn = call.asFunction();
            calls.add(LlmMessage.ToolCall.builder()
                    .id(fn.id())
                    .name(fn.function().name())
                    .argumentsJson(fn.function().arguments())
                    .build());
        }
        return LlmMessage.assistant(text, calls);
    }

    private FunctionParameters toFunctionParameters(com.fasterxml.jackson.databind.node.ObjectNode schema) {
        FunctionParameters.Builder b = FunctionParameters.builder();
        schema.fields().forEachRemaining(e ->
                b.putAdditionalProperty(e.getKey(), com.openai.core.JsonValue.fromJsonNode(e.getValue())));
        return b.build();
    }

    private LlmResponse.FinishReason mapFinishReason(ChatCompletion.Choice.FinishReason reason) {
        if (ChatCompletion.Choice.FinishReason.TOOL_CALLS.equals(reason)) {
            return LlmResponse.FinishReason.TOOL_CALLS;
        }
        if (ChatCompletion.Choice.FinishReason.LENGTH.equals(reason)) {
            return LlmResponse.FinishReason.LENGTH;
        }
        return LlmResponse.FinishReason.STOP;
    }

    /** 流式 chunk 的 FinishReason 是与阻塞响应不同的类型，单独映射 */
    private LlmResponse.FinishReason mapChunkFinishReason(ChatCompletionChunk.Choice.FinishReason reason) {
        if (ChatCompletionChunk.Choice.FinishReason.TOOL_CALLS.equals(reason)) {
            return LlmResponse.FinishReason.TOOL_CALLS;
        }
        if (ChatCompletionChunk.Choice.FinishReason.LENGTH.equals(reason)) {
            return LlmResponse.FinishReason.LENGTH;
        }
        return LlmResponse.FinishReason.STOP;
    }

    private TokenUsage toTokenUsage(CompletionUsage usage) {
        if (usage == null) {
            return null;
        }
        return TokenUsage.builder()
                .inputTokens(usage.promptTokens())
                .outputTokens(usage.completionTokens())
                .cacheReadTokens(usage.promptTokensDetails()
                        .flatMap(CompletionUsage.PromptTokensDetails::cachedTokens).orElse(null))
                .cacheCreationTokens(usage.promptTokensDetails()
                        .flatMap(CompletionUsage.PromptTokensDetails::cacheWriteTokens).orElse(null))
                .timestamp(LocalDateTime.now().format(TS_FMT))
                .build();
    }
}
