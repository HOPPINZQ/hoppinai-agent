package com.hoppinzq.agent.tool.bus;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一个 teammate 的独立运行循环（自己的 LLM 会话 + 自己的 mailbox）。
 * <p>
 * 与 teams 模块相比，protocols 版本：
 * <ul>
 *   <li>用 idle loop 替代硬编码 10 轮上限（保留 50 轮安全上限防止死循环）</li>
 *   <li>每轮先 poll 自己的 inbox，遇到 {@code shutdown_request} 就回 {@code shutdown_response} 并退出</li>
 *   <li>{@code message} 类型消息作为 user 输入注入会话</li>
 *   <li>允许 teammate 自行发出 {@code plan_approval_request}（用 send_message 工具触发）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class TeammateRunner implements Runnable {

    private static final int MAX_ITERATIONS = 50;
    private static final long IDLE_SLEEP_MS = 500L;

    private final AnthropicClient client;
    private final String model;
    private final String name;
    private final String role;
    private final String initialPrompt;
    private final MessageBus bus;
    private final List<ToolDefinition> teammateTools;
    /** lead 的协议注册表引用，teammate 发 plan_approval_request 时用其生成 requestId */
    private final ProtocolRegistry protocolRegistry;

    private final List<MessageParam> messages = new ArrayList<>();

    public TeammateRunner(AnthropicClient client, String model, String name, String role,
                          String initialPrompt, MessageBus bus,
                          List<ToolDefinition> teammateTools,
                          ProtocolRegistry protocolRegistry) {
        this.client = client;
        this.model = model;
        this.name = name;
        this.role = role;
        this.initialPrompt = initialPrompt;
        this.bus = bus;
        this.teammateTools = teammateTools;
        this.protocolRegistry = protocolRegistry;
    }

    @Override
    public void run() {
        System.out.printf("\u001b[95m[team]\u001b[0m teammate %s 上线，角色=%s%n", name, role);
        // 第一轮把 initialPrompt 当作用户输入
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(initialPrompt == null ? ("你好，你是 " + name + "，角色：" + role) : initialPrompt)
                .build());

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // 1) 先 poll 自己的 inbox
            List<MailboxMessage> inbox = bus.readInbox(name);
            boolean shutdownRequested = false;
            List<String> userInputs = new ArrayList<>();
            for (MailboxMessage m : inbox) {
                String t = m.getType() == null ? "message" : m.getType();
                if ("shutdown_request".equals(t)) {
                    String requestId = extractRequestId(m.getContent());
                    // 回 shutdown_response
                    String ackPayload = "{\"requestId\":\"" + (requestId == null ? "" : requestId)
                            + "\",\"ack\":\"bye\"}";
                    bus.send(name, "lead", ackPayload, "shutdown_response");
                    System.out.printf("\u001b[95m[team]\u001b[0m %s 收到 shutdown_request，已回执并准备退出%n", name);
                    shutdownRequested = true;
                    break;
                } else if ("plan_approval_response".equals(t)) {
                    userInputs.add("[plan_approval_response] " + m.getFrom() + " -> " + m.getContent());
                } else {
                    // message
                    userInputs.add(m.getFrom() + ": " + m.getContent());
                }
            }
            if (shutdownRequested) {
                break;
            }

            // 2) 没有任何待响应的消息，且第一轮已经处理完 → 进入 idle 睡眠
            if (i > 0 && messages.isEmpty() == false && userInputs.isEmpty()) {
                // 检查上一轮是否还有未完成的工具循环；如果没有，则 sleep
                // 简化版：直接 sleep 后继续 poll
                try {
                    Thread.sleep(IDLE_SLEEP_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // sleep 后清空历史最后一轮用户消息标记，避免无限 LLM 调用
                // 这里仅继续轮询 inbox，不强制发起新 LLM 调用
                continue;
            }

            // 把 inbox 里收到的 message 作为新一轮 user 消息注入
            for (String ui : userInputs) {
                messages.add(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(ui)
                        .build());
            }

            // 3) 执行一轮 LLM 调用 + 工具循环
            try {
                runOneLlmRound();
            } catch (Exception e) {
                System.err.printf("[team] %s LLM 调用异常：%s%n", name, e.getMessage());
                break;
            }
        }
        System.out.printf("\u001b[95m[team]\u001b[0m teammate %s 下线%n", name);
    }

    /** 一轮完整的 LLM + 工具循环，内部最多 10 次工具往返。 */
    private void runOneLlmRound() {
        Message message;
        try {
            message = chatMessage();
        } catch (Exception e) {
            System.err.printf("[team] %s 首次 LLM 调用失败：%s%n", name, e.getMessage());
            return;
        }
        messages.add(message.toParam());

        int toolRound = 0;
        while (toolRound++ < 10) {
            List<ContentBlockParam> toolResults = new ArrayList<>();
            boolean hasToolUse = false;

            for (ContentBlock content : message.content()) {
                if (content.isText()) {
                    Optional<TextBlock> text = content.text();
                    String result = text.map(TextBlock::text).orElse("");
                    System.out.printf("\u001b[93m[%s]\u001b[0m %s%n", name, result);
                } else if (content.isToolUse()) {
                    hasToolUse = true;
                    ToolUseBlock toolUse = content.asToolUse();
                    System.out.printf("\u001b[96m[%s 工具]\u001b[0m %s(%s)%n", name, toolUse.name(), toolUse._input());

                    String toolResult = null;
                    Exception toolError = null;
                    boolean found = false;
                    for (ToolDefinition tool : teammateTools) {
                        if (tool.getName().equals(toolUse.name())) {
                            found = true;
                            try {
                                toolResult = invokeTeammateTool(tool, toolUse);
                                System.out.printf("\u001b[92m[%s 结果]\u001b[0m %s%n", name, toolResult);
                            } catch (Exception e) {
                                toolError = e;
                                System.err.printf("[team] %s 工具 %s 异常：%s%n", name, toolUse.name(), e.getMessage());
                            }
                            break;
                        }
                    }
                    if (!found) {
                        toolError = new Exception("工具 " + toolUse.name() + " 不在 teammate 可用列表");
                    }
                    if (toolError != null) {
                        toolResults.add(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUse.id())
                                        .content(toolError.getMessage())
                                        .isError(true)
                                        .build()));
                    } else {
                        toolResults.add(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUse.id())
                                        .content(toolResult)
                                        .isError(false)
                                        .build()));
                    }
                }
            }
            if (!hasToolUse) break;

            MessageParam.Content content = MessageParam.Content.ofBlockParams(toolResults);
            MessageParam toolResultMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(content)
                    .build();
            messages.add(toolResultMessage);
            try {
                message = chatMessage();
            } catch (Exception e) {
                System.err.printf("[team] %s 工具后 LLM 调用失败：%s%n", name, e.getMessage());
                return;
            }
            messages.add(message.toParam());
        }
    }

    private Message chatMessage() {
        List<ToolUnion> anthropicTools = new ArrayList<>();
        for (ToolDefinition tool : teammateTools) {
            anthropicTools.add(ToolUnion.ofTool(
                    Tool.builder()
                            .name(tool.getName())
                            .description(tool.getDescription())
                            .inputSchema(tool.getInputSchema())
                            .build()
            ));
        }
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .messages(messages)
                .maxTokens(2048)
                .temperature(0.5D);
        if (!anthropicTools.isEmpty()) {
            b.tools(anthropicTools);
        }
        b.system("你是 teammate " + name + "，角色：" + role
                + "。你可以用 send_message 工具向 lead 发消息（to=lead），"
                + "如果需要 lead 审批方案，type 用 plan_approval_request，并把 requestId/plan 放进 content JSON。"
                + "收到 shutdown_request 时请立即退出。");
        return client.messages().create(b.build());
    }

    private String invokeTeammateTool(ToolDefinition tool, ToolUseBlock toolUse) throws Exception {
        JsonValue input = toolUse._input();
        if (tool.getType() == null) {
            Optional<Map<String, JsonValue>> object = input.asObject();
            if (object.isPresent()) {
                Map<String, Object> callTool = new HashMap<>();
                callTool.put("input", object.get());
                callTool.put("tool_name", tool.getName());
                // teammate 自己的名字通过 ThreadLocal 传入
                callTool.put("__from", name);
                return tool.getFunction().apply(
                        com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER.writeValueAsString(callTool));
            }
            throw new IllegalArgumentException("工具 " + tool.getName() + " 参数转换失败");
        }
        return tool.getFunction().apply(
                java.util.Objects.requireNonNull(input.convert(tool.getType())).toString());
    }

    private static String extractRequestId(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER.readValue(content, Map.class);
            Object v = map.get("requestId");
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
