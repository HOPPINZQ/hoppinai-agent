package com.hoppinzq.agent.tool.bus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MAX_TOKENS;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;
import static com.hoppinzq.agent.constant.AIConstants.TEMPERATURE;

/**
 * teammate 自己的 agent 主循环，运行在守护线程里。
 *
 * <p>每个 teammate 维护自己独立的 {@code List<LlmMessage>}，与 lead 完全隔离；
 * 二者之间唯一的通信通道是 {@link MessageBus}（{@code <ROOT>/.mailboxes/*.jsonl}）。
 *
 * <p>循环流程：
 * <ol>
 *   <li>读 inbox；遇到 {@code shutdown_request} 直接退出（protocols 链用，teams 模块不会触发）。</li>
 *   <li>把 lead 发来的 {@code message} 注入成新的 user 消息。</li>
 *   <li>调用 LLM；处理工具调用。</li>
 *   <li>10 轮硬上限 / {@code finish_reason != tool_calls} / 本轮无 tool_use → 停止。</li>
 *   <li>停止后给 lead 发一条 {@code send_message} 收尾。</li>
 * </ol>
 *
 * @author hoppinzq
 */
public class TeammateRunner implements Runnable {

    private static final int MAX_ROUNDS = 10;

    private final LlmProvider provider;
    private final String model;
    private final String name;
    private final String role;
    private final String initialPrompt;
    private final MessageBus bus;
    private final List<ToolDefinition> teammateTools;
    private final List<LlmMessage> messageParams = new ArrayList<>();

    public TeammateRunner(LlmProvider provider,
                          String model,
                          String name,
                          String role,
                          String initialPrompt,
                          MessageBus bus,
                          List<ToolDefinition> teammateTools) {
        this.provider = provider;
        this.model = model;
        this.name = name;
        this.role = role;
        this.initialPrompt = initialPrompt;
        this.bus = bus;
        this.teammateTools = teammateTools;
    }

    @Override
    public void run() {
        // 标记当前线程的 teammate 名字，供 Tools.sendMessage 区分发送者
        com.hoppinzq.agent.tool.Tools.setCurrentTeammateName(name);
        try {
            System.out.printf("\u001b[95m[teammate %s]\u001b[0m 启动，role=%s%n", name, role);

            String systemPrompt = buildSystemPrompt(name, role);
            // 把 lead 传过来的初始 prompt 当作第一条 user 消息
            messageParams.add(LlmMessage.user(initialPrompt == null ? "开始工作。" : initialPrompt));

            boolean alreadySentFinal = false;
            String lastText = "";

            for (int round = 0; round < MAX_ROUNDS; round++) {
                // 1. 先消费 inbox
                List<MailboxMessage> inbox = MessageBus.readInbox(name);
                for (MailboxMessage m : inbox) {
                    if ("shutdown_request".equals(m.getType())) {
                        System.out.printf("\u001b[95m[teammate %s]\u001b[0m 收到 shutdown_request，退出%n", name);
                        MessageBus.send(name, "lead", "shutdown ok", "message");
                        return;
                    }
                    if ("message".equals(m.getType())) {
                        messageParams.add(LlmMessage.user("[lead message] " + m.getContent()));
                    }
                }

                // 2. 调用 LLM
                LlmResponse response = chatMessage(systemPrompt);
                LlmMessage message = response.getMessage();
                if (message == null) {
                    break;
                }
                messageParams.add(message);

                // 3. 处理文本与工具调用
                List<LlmMessage> toolResults = new ArrayList<>();
                boolean hasToolUse = false;
                if (message.getText() != null && !message.getText().isBlank()) {
                    lastText = message.getText();
                    System.out.printf("\u001b[95m[teammate %s]\u001b[0m %s%n", name, message.getText());
                }
                for (LlmMessage.ToolCall call : message.getToolCalls()) {
                    hasToolUse = true;
                    System.out.printf("\u001b[95m[teammate %s]\u001b[0m 工具: %s(%s)%n",
                            name, call.getName(), call.getArgumentsJson());
                    String toolResult;
                    try {
                        toolResult = invokeTool(call);
                    } catch (Exception e) {
                        toolResult = e.getMessage() == null ? "工具执行异常" : e.getMessage();
                        toolResult = "错误: " + toolResult;
                    }
                    System.out.printf("\u001b[95m[teammate %s]\u001b[0m 结果: %s%n", name, toolResult);
                    // 如果 teammate 通过 send_message 给 lead 发了消息，标记避免重复发
                    if ("send_message".equals(call.getName())) {
                        alreadySentFinal = true;
                    }
                    toolResults.add(LlmMessage.tool(call.getId(), toolResult));
                }

                // 4. 判停：无工具调用即本轮结束；finish_reason 非 tool_calls 时把工具结果回灌一次再退出
                if (!hasToolUse) {
                    break;
                }
                messageParams.addAll(toolResults);
                if (response.getFinishReason() != LlmResponse.FinishReason.TOOL_CALLS) {
                    break;
                }
            }

            // 6. 收尾：若 teammate 还没主动 send_message，补一条
            if (!alreadySentFinal) {
                String summary = lastText.isBlank()
                        ? ("已完成 " + role + " 任务（无文本输出）")
                        : lastText;
                MessageBus.send(name, "lead", name + " finished: " + summary, "message");
            }
            System.out.printf("\u001b[95m[teammate %s]\u001b[0m 结束%n", name);
        } catch (Exception e) {
            System.err.printf("[teammate %s] 异常: %s%n", name, e.getMessage());
            e.printStackTrace();
            try {
                MessageBus.send(name, "lead",
                        name + " error: " + (e.getMessage() == null ? "未知异常" : e.getMessage()),
                        "message");
            } catch (Exception ignore) {
                // 收尾失败忽略
            }
        } finally {
            com.hoppinzq.agent.tool.Tools.clearCurrentTeammateName();
        }
    }

    private String invokeTool(LlmMessage.ToolCall call) throws Exception {
        for (ToolDefinition tool : teammateTools) {
            if (tool.getName().equals(call.getName())) {
                JsonNode input = OBJECT_MAPPER.readTree(
                        call.getArgumentsJson() == null || call.getArgumentsJson().isBlank()
                                ? "{}" : call.getArgumentsJson());
                if (tool.getType() == null) {
                    ObjectNode root = OBJECT_MAPPER.createObjectNode();
                    root.set("input", input);
                    root.put("tool_name", tool.getName());
                    return tool.getFunction().apply(root.toString());
                }
                Object pojo = OBJECT_MAPPER.treeToValue(input, tool.getType());
                return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(pojo));
            }
        }
        throw new IllegalStateException("teammate 找不到工具: " + call.getName());
    }

    private LlmResponse chatMessage(String systemPrompt) {
        return provider.complete(LlmRequest.builder()
                .model(model)
                .systemPrompt(systemPrompt)
                .messages(messageParams)
                .tools(teammateTools)
                .maxTokens(MAX_TOKENS)
                .temperature(TEMPERATURE)
                .build());
    }

    /**
     * teammate 的系统提示。告诉它自己的名字 / 角色，能用哪些工具，
     * 干完活后调用 send_message(to="lead") 汇报。
     */
    static String buildSystemPrompt(String name, String role) {
        StringBuilder toolList = new StringBuilder();
        toolList.append("- bash: 执行 shell 命令\n");
        toolList.append("- read_file: 读文件\n");
        toolList.append("- write_file: 写文件\n");
        toolList.append("- send_message(to, content): 给 lead 发消息\n");
        return String.format("""
                你是一个 teammate agent，名字叫 %s，角色是「%s」。
                你运行在独立的守护线程里，和 lead 之间通过文件邮箱通信。

                你可以使用以下工具完成 lead 派给你的任务：
                %s
                工作流程：
                1. 仔细阅读 lead 派给你的初始任务。
                2. 调用 bash / read_file / write_file 完成具体工作。
                3. 完成后调用 send_message(to="lead", content="<结果摘要>") 汇报。
                不要反复确认，不要请求 lead 确认，尽可能自主完成。
                最多 10 轮 LLM 调用，务必在轮数用完前 send_message 收尾。
                """, name, role == null ? "通用助手" : role, toolList);
    }
}
