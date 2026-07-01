package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.tool.ToolDefinition;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类
 * @author hoppinzq
 */
@Data
public class ZQAgent {
    private String systemPrompt;
    private final Scanner scanner;
    private final String model;
    protected final AnthropicClient client;
    private final List<ToolDefinition> tools;
    protected final List<MessageParam> messageParams = new ArrayList<>();
    private String taskResult;
    private boolean taskCompleted = false;
    private AgentCommandHandler commandHandler;

    public ZQAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
    }

    public void setCommandHandler(AgentCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    public void run() {
        System.out.println("开始对话吧");
        while (true) {
            System.out.print("\u001b[94m你\u001b[0m: ");
            String userInput = scanner.nextLine();
            if (userInput.isEmpty()) {
                continue;
            }

            // 特殊命令：不发送给 LLM
            if (commandHandler != null && commandHandler.isCommand(userInput)) {
                commandHandler.handleCommand(userInput);
                continue;
            }

            MessageParam userMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(userInput)
                    .build();
            messageParams.add(userMessage);

            Message message;
            try {
                message = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                continue;
            }
            messageParams.add(message.toParam());
            recordUsageIfNeeded(message);
            printText(message);

            while (isToolUse(message)) {
                MessageParam toolResultMessage = executeToolCalls(message);
                messageParams.add(toolResultMessage);
                try {
                    message = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                messageParams.add(message.toParam());
                recordUsageIfNeeded(message);
                printText(message);
            }
            warnIfTruncated(message);
        }
    }

    private void printText(Message message) {
        for (ContentBlock content : message.content()) {
            if (content.isText()) {
                String text = content.text().map(TextBlock::text).orElse("");
                if (!text.isBlank()) {
                    System.out.printf("\u001b[93mAI\u001b[0m: %s%n", text);
                }
            }
        }
    }

    private boolean isToolUse(Message message) {
        return message.stopReason()
                .map(StopReason.TOOL_USE::equals)
                .orElse(false);
    }

    private void warnIfTruncated(Message message) {
        boolean maxTokens = message.stopReason()
                .map(StopReason.MAX_TOKENS::equals)
                .orElse(false);
        if (maxTokens) {
            System.out.printf("\u001b[91m[警告]\u001b[0m 本轮回复被 max_tokens=%d 截断，工具调用可能不完整。建议调大 MAX_TOKENS。%n",
                    MAX_TOKENS);
        }
    }

    private MessageParam executeToolCalls(Message message) {
        List<ContentBlockParam> toolResults = new ArrayList<>();
        for (ContentBlock content : message.content()) {
            if (!content.isToolUse()) {
                continue;
            }
            ToolUseBlock toolUse = content.asToolUse();
            System.out.printf("\u001b[96m工具\u001b[0m: %s(%s)%n", toolUse.name(), toolUse._input());

            String toolResult = null;
            Exception toolError = null;
            ToolDefinition matched = null;
            for (ToolDefinition tool : tools) {
                if (tool.getName().equals(toolUse.name())) {
                    matched = tool;
                    break;
                }
            }
            if (matched == null) {
                toolError = new Exception("工具 '" + toolUse.name() + "' 没有找到");
                System.out.printf("\u001b[91m错误\u001b[0m: %s%n", toolError.getMessage());
            } else {
                try {
                    toolResult = invokeTool(matched, toolUse._input());
                    System.out.printf("\u001b[92m结果\u001b[0m: %s%n", toolResult);
                } catch (Exception e) {
                    toolError = e;
                    System.out.printf("\u001b[91m错误\u001b[0m: %s%n", e.getMessage());
                    e.printStackTrace();
                }
            }

            toolResults.add(ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder()
                            .toolUseId(toolUse.id())
                            .content(toolError != null ? toolError.getMessage() : toolResult)
                            .isError(toolError != null)
                            .build()
            ));
        }
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(MessageParam.Content.ofBlockParams(toolResults))
                .build();
    }

    /** 子类（如 Agent04）可重写此钩子，在工具执行后做额外处理（如待办提醒）。 */
    protected void onToolExecution(List<ContentBlockParam> toolResults) {
    }

    /**
     * 记录本次 LLM 调用的 token 使用情况（若设置了 SessionManager）。
     * 注意：agent-04 当前没有 SessionManager，此方法为预留接口。
     */
    private void recordUsageIfNeeded(Message message) {
        // 暂无 SessionManager，预留接口
    }

    private String invokeTool(ToolDefinition tool, JsonValue input) {
        if (tool.getType() == null) {
            if (input.asObject().isEmpty()) {
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数不是 JSON 对象");
            }
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.set("input", input.convert(JsonNode.class));
            root.put("tool_name", tool.getName());
            return tool.getFunction().apply(root.toString());
        } else {
            return tool.getFunction().apply(Objects.requireNonNull(input.convert(tool.getType())).toString());
        }
    }

    protected Message chatMessage(List<MessageParam> messageParams){
        // 准备工具配置
        List<ToolUnion> anthropicTools = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            anthropicTools.add(ToolUnion.ofTool(
                    Tool.builder()
                            .name(tool.getName())
                            .description(tool.getDescription())
                            .inputSchema(tool.getInputSchema())
                            .build()
            ));
        }
        MessageCreateParams.Builder messageBuilder = MessageCreateParams.builder()
                .model(model)
                .messages(messageParams)
                .tools(anthropicTools);

        if(systemPrompt != null && !systemPrompt.isEmpty()){
            messageBuilder.system(systemPrompt);
        }

         messageBuilder.maxTokens(MAX_TOKENS);
        messageBuilder.temperature(TEMPERATURE);

        MessageCreateParams params = messageBuilder.build();
        return client.messages().create(params);
    }

    public String runTask(String prompt) {
        MessageParam userMessage = MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(prompt)
                .build();
        messageParams.add(userMessage);

        while (true) {
            Message message;
            try {
                message = chatMessage(messageParams);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            messageParams.add(message.toParam());

            for (ContentBlock content : message.content()) {
                if (content.isText()) {
                    Optional<TextBlock> text = content.text();
                    String result = text.map(TextBlock::text).orElse("");
                    if (!result.isBlank()) {
                        System.out.printf("\u001b[94m（子）AI\u001b[0m: %s%n", result);
                    }
                }
            }

            // 没有工具调用：把文本拼接后作为任务结果返回
            if (!isToolUse(message)) {
                warnIfTruncated(message);
                return message.content().stream()
                        .filter(ContentBlock::isText)
                        .map(cb -> cb.text().get().text())
                        .reduce("", (a, b) -> a + b);
            }

            // 处理本轮全部工具调用；其中 task_completed 直接短路返回
            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ContentBlock content : message.content()) {
                if (!content.isToolUse()) {
                    continue;
                }
                ToolUseBlock toolUse = content.asToolUse();
                System.out.printf("\u001b[96m（子）工具\u001b[0m: %s(%s)%n", toolUse.name(), toolUse._input());

                if ("task_completed".equals(toolUse.name())) {
                    try {
                        JsonValue input = toolUse._input();
                        Optional<Map<String, JsonValue>> object = input.asObject();
                        if (object.isPresent()) {
                            JsonValue res = object.get().get("result");
                            if (res != null && res.asString().isPresent()) {
                                return res.asString().get().toString();
                            }
                        }
                        return "Task completed.";
                    } catch (Exception e) {
                        return "Error parsing task_completed: " + e.getMessage();
                    }
                }

                String toolResult = null;
                Exception toolError = null;
                ToolDefinition matched = null;
                for (ToolDefinition tool : tools) {
                    if (tool.getName().equals(toolUse.name())) {
                        matched = tool;
                        break;
                    }
                }
                if (matched == null) {
                    toolError = new Exception("工具 '" + toolUse.name() + "' 没有找到");
                    System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", toolError.getMessage());
                } else {
                    try {
                        toolResult = invokeTool(matched, toolUse._input());
                        System.out.printf("\u001b[92m（子）结果\u001b[0m: %s%n", toolResult);
                    } catch (Exception e) {
                        toolError = e;
                        System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", e.getMessage());
                        e.printStackTrace();
                    }
                }

                toolResults.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(toolUse.id())
                                .content(toolError != null ? toolError.getMessage() : toolResult)
                                .isError(toolError != null)
                                .build()
                ));
            }

            MessageParam toolResultMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(MessageParam.Content.ofBlockParams(toolResults))
                    .build();
            messageParams.add(toolResultMessage);
        }
    }
}
