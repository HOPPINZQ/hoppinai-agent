package com.hoppinzq.agent.tool.bus;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * teammate 自己的 agent 主循环，运行在守护线程里。
 *
 * <p>每个 teammate 维护自己独立的 {@code List<MessageParam>}，与 lead 完全隔离；
 * 二者之间唯一的通信通道是 {@link MessageBus}（{@code <ROOT>/.mailboxes/*.jsonl}）。
 *
 * <p>循环流程：
 * <ol>
 *   <li>读 inbox；遇到 {@code shutdown_request} 直接退出（protocols 链用，teams 模块不会触发）。</li>
 *   <li>把 lead 发来的 {@code message} 注入成新的 user 消息。</li>
 *   <li>调用 LLM；处理 {@code tool_use} 块。</li>
 *   <li>10 轮硬上限 / {@code stop_reason != tool_use} / 本轮无 tool_use → 停止。</li>
 *   <li>停止后给 lead 发一条 {@code send_message} 收尾。</li>
 * </ol>
 *
 * @author hoppinzq
 */
public class TeammateRunner implements Runnable {

    private static final int MAX_ROUNDS = 10;

    private final AnthropicClient client;
    private final String model;
    private final String name;
    private final String role;
    private final String initialPrompt;
    private final MessageBus bus;
    private final List<ToolDefinition> teammateTools;
    private final List<MessageParam> messageParams = new ArrayList<>();

    public TeammateRunner(AnthropicClient client,
                          String model,
                          String name,
                          String role,
                          String initialPrompt,
                          MessageBus bus,
                          List<ToolDefinition> teammateTools) {
        this.client = client;
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
            messageParams.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(initialPrompt == null ? "开始工作。" : initialPrompt)
                    .build());

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
                        messageParams.add(MessageParam.builder()
                                .role(MessageParam.Role.USER)
                                .content("[lead message] " + m.getContent())
                                .build());
                    }
                }

                // 2. 调用 LLM
                Message message = chatMessage(systemPrompt);
                messageParams.add(message.toParam());

                // 3. 处理 content blocks
                List<ContentBlockParam> toolResults = new ArrayList<>();
                boolean hasToolUse = false;
                for (ContentBlock content : message.content()) {
                    if (content.isText()) {
                        String text = content.text().map(TextBlock::text).orElse("");
                        lastText = text;
                        if (!text.isBlank()) {
                            System.out.printf("\u001b[95m[teammate %s]\u001b[0m %s%n", name, text);
                        }
                    } else if (content.isToolUse()) {
                        hasToolUse = true;
                        ToolUseBlock toolUse = content.asToolUse();
                        System.out.printf("\u001b[95m[teammate %s]\u001b[0m 工具: %s(%s)%n",
                                name, toolUse.name(), toolUse._input());
                        String toolResult;
                        boolean isError = false;
                        try {
                            toolResult = invokeTool(toolUse);
                        } catch (Exception e) {
                            toolResult = e.getMessage() == null ? "工具执行异常" : e.getMessage();
                            isError = true;
                        }
                        System.out.printf("\u001b[95m[teammate %s]\u001b[0m 结果: %s%n", name, toolResult);
                        // 如果 teammate 通过 send_message 给 lead 发了消息，标记避免重复发
                        if ("send_message".equals(toolUse.name())) {
                            alreadySentFinal = true;
                        }
                        toolResults.add(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUse.id())
                                        .content(toolResult)
                                        .isError(isError)
                                        .build()
                        ));
                    }
                }

                // 4. 判停：非 tool_use 即本轮结束
                if (!hasToolUse) {
                    break;
                }
                if (message.stopReason().isEmpty()
                        || message.stopReason().get() != StopReason.TOOL_USE) {
                    // 把 tool 结果回灌一次再退出
                    MessageParam toolResultMessage = MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(MessageParam.Content.ofBlockParams(toolResults))
                            .build();
                    messageParams.add(toolResultMessage);
                    break;
                }

                // 5. 继续下一轮：把 tool 结果塞回去
                MessageParam toolResultMessage = MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(MessageParam.Content.ofBlockParams(toolResults))
                        .build();
                messageParams.add(toolResultMessage);
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

    private String invokeTool(ToolUseBlock toolUse) throws Exception {
        for (ToolDefinition tool : teammateTools) {
            if (tool.getName().equals(toolUse.name())) {
                JsonValue input = toolUse._input();
                if (tool.getType() == null) {
                    return tool.getFunction().apply(
                            com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER.writeValueAsString(
                                    Map.of("input", input.asObject().orElse(Map.of()),
                                            "tool_name", tool.getName())));
                }
                return tool.getFunction().apply(
                        java.util.Objects.requireNonNull(input.convert(tool.getType())).toString());
            }
        }
        throw new IllegalStateException("teammate 找不到工具: " + toolUse.name());
    }

    private Message chatMessage(String systemPrompt) {
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
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .messages(messageParams)
                .tools(anthropicTools)
                .system(systemPrompt)
                .maxTokens(com.hoppinzq.agent.constant.AIConstants.MAX_TOKENS)
                .temperature(com.hoppinzq.agent.constant.AIConstants.TEMPERATURE)
                .build();
        return client.messages().create(params);
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
