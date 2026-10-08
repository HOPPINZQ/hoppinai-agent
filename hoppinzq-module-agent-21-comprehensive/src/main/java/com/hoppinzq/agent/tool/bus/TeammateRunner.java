package com.hoppinzq.agent.tool.bus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

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
 * <p>只面向协议中立的 {@link LlmProvider}/{@link LlmMessage} 编程。
 *
 * @author hoppinzq
 */
public class TeammateRunner implements Runnable {

    private static final int MAX_ITERATIONS = 50;
    private static final long IDLE_SLEEP_MS = 500L;
    /** 单轮内最多 10 次工具往返 */
    private static final int MAX_TOOL_ROUNDS = 10;
    private static final double TEMPERATURE = 0.5D;

    private final LlmProvider provider;
    private final String model;
    private final String name;
    private final String role;
    private final String initialPrompt;
    private final MessageBus bus;
    private final List<ToolDefinition> teammateTools;
    /** lead 的协议注册表引用，teammate 发 plan_approval_request 时用其生成 requestId */
    private final ProtocolRegistry protocolRegistry;

    private final List<LlmMessage> messages = new ArrayList<>();

    public TeammateRunner(LlmProvider provider, String model, String name, String role,
                          String initialPrompt, MessageBus bus,
                          List<ToolDefinition> teammateTools,
                          ProtocolRegistry protocolRegistry) {
        this.provider = provider;
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
        // 标记当前线程的 teammate 名字，供 Tools.sendMessage 区分发送者
        com.hoppinzq.agent.tool.Tools.setCurrentTeammateName(name);
        try {
            System.out.printf("\u001b[95m[team]\u001b[0m teammate %s 上线，角色=%s%n", name, role);
            // 第一轮把 initialPrompt 当作用户输入
            messages.add(LlmMessage.user(initialPrompt == null
                    ? ("你好，你是 " + name + "，角色：" + role) : initialPrompt));

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
                if (i > 0 && !messages.isEmpty() && userInputs.isEmpty()) {
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
                    messages.add(LlmMessage.user(ui));
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
        } finally {
            com.hoppinzq.agent.tool.Tools.clearCurrentTeammateName();
        }
    }

    /** 一轮完整的 LLM + 工具循环，内部最多 10 次工具往返。 */
    private void runOneLlmRound() {
        LlmResponse response;
        try {
            response = chatMessage();
        } catch (Exception e) {
            System.err.printf("[team] %s 首次 LLM 调用失败：%s%n", name, e.getMessage());
            return;
        }
        LlmMessage message = response.getMessage();
        if (message == null) {
            return;
        }
        messages.add(message);

        int toolRound = 0;
        while (toolRound++ < MAX_TOOL_ROUNDS) {
            List<LlmMessage> toolResults = new ArrayList<>();

            // 文本输出（DeepSeek 等后端返回 tool_calls 时文本常为 null）
            String text = message.getText();
            if (text != null && !text.isBlank()) {
                System.out.printf("\u001b[93m[%s]\u001b[0m %s%n", name, text);
            }

            for (LlmMessage.ToolCall call : message.getToolCalls()) {
                System.out.printf("\u001b[96m[%s 工具]\u001b[0m %s(%s)%n",
                        name, call.getName(), call.getArgumentsJson());

                String toolResult;
                try {
                    ToolDefinition matched = null;
                    for (ToolDefinition tool : teammateTools) {
                        if (tool.getName().equals(call.getName())) {
                            matched = tool;
                            break;
                        }
                    }
                    if (matched == null) {
                        throw new IllegalStateException("工具 " + call.getName() + " 不在 teammate 可用列表");
                    }
                    toolResult = invokeTeammateTool(matched, call.getArgumentsJson());
                    System.out.printf("\u001b[92m[%s 结果]\u001b[0m %s%n", name, toolResult);
                } catch (Exception e) {
                    toolResult = "错误: " + e.getMessage();
                    System.err.printf("[team] %s 工具 %s 异常：%s%n", name, call.getName(), e.getMessage());
                }
                toolResults.add(LlmMessage.tool(call.getId(), toolResult));
            }
            if (toolResults.isEmpty()) {
                break;
            }

            messages.addAll(toolResults);
            try {
                response = chatMessage();
            } catch (Exception e) {
                System.err.printf("[team] %s 工具后 LLM 调用失败：%s%n", name, e.getMessage());
                return;
            }
            message = response.getMessage();
            if (message == null) {
                return;
            }
            messages.add(message);
        }
    }

    private LlmResponse chatMessage() {
        return provider.complete(LlmRequest.builder()
                .model(model)
                .messages(messages)
                .tools(teammateTools)
                .maxTokens(2048)
                .temperature(TEMPERATURE)
                .systemPrompt("你是 teammate " + name + "，角色：" + role
                        + "。你可以用 send_message 工具向 lead 发消息（to=lead），"
                        + "如果需要 lead 审批方案，type 用 plan_approval_request，并把 requestId/plan 放进 content JSON。"
                        + "收到 shutdown_request 时请立即退出。")
                .build());
    }

    private String invokeTeammateTool(ToolDefinition tool, String argumentsJson) throws Exception {
        JsonNode input = OBJECT_MAPPER.readTree(
                argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        if (tool.getType() == null) {
            if (!input.isObject()) {
                throw new IllegalArgumentException("工具 " + tool.getName() + " 参数不是 JSON 对象");
            }
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.set("input", input);
            root.put("tool_name", tool.getName());
            // teammate 自己的名字通过包装入参传给 Tools.sendMessage
            root.put("__from", name);
            return tool.getFunction().apply(root.toString());
        }
        Object pojo = OBJECT_MAPPER.treeToValue(input, tool.getType());
        return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(pojo));
    }

    private static String extractRequestId(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = OBJECT_MAPPER.readValue(content, java.util.Map.class);
            Object v = map.get("requestId");
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
