package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.session.SubAgentSessionResult;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.MAX_TOKENS;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

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

    /**
     * 会话管理器
     */
    protected SessionManager sessionManager;

    public ZQAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
    }

    /**
     * 设置会话管理器，同时初始化命令处理器。
     */
    public void setSessionManager(SessionManager sessionManager, SkillLoader skillLoader) {
        this.sessionManager = sessionManager;
        this.commandHandler = sessionManager != null ? new AgentCommandHandler(sessionManager,skillLoader) : null;
    }

    public void run() {
        if (sessionManager != null) {
            int n = sessionManager.historySize();
            if (n > 0) {
                sessionManager.populate(messageParams);
                System.out.printf("\u001b[90m已恢复会话 %s，共 %d 条历史消息\u001b[0m%n",
                        sessionManager.getSessionId(), n);
            } else {
                System.out.printf("\u001b[90m新会话 %s\u001b[0m%n", sessionManager.getSessionId());
            }
        }
        System.out.println("开始对话吧（输入 /stats 查看统计，/usage 查看明细，/skills 查看技能，/exit 退出）");
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
            appendMessage(userMessage);

            Message message;
            try {
                message = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                continue;
            }
            appendMessage(message.toParam());
            recordUsageIfNeeded(message);
            printText(message);

            while (isToolUse(message)) {
                MessageParam toolResultMessage = executeToolCalls(message);
                appendMessage(toolResultMessage);
                try {
                    message = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                appendMessage(message.toParam());
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
     */
    private void recordUsageIfNeeded(Message message) {
        if (sessionManager != null && message.usage() != null) {
            sessionManager.recordUsage(message.usage());
        }
    }

    /**
     * 向 messageParams 追加一条消息；若设置了 {@link SessionManager}，
     * 同步持久化。所有需要记录历史的追加都应走此方法。
     */
    protected void appendMessage(MessageParam param) {
        messageParams.add(param);
        if (sessionManager != null) {
            sessionManager.onMessageAppended(param);
        }
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

    /**
     * 执行任务并返回包含 token 使用情况的结果
     *
     * @param prompt 任务提示词
     * @return 包含结果和 token 使用情况的 SubAgentResult
     */
    public SubAgentSessionResult runTaskWithTokenUsage(String prompt) {
        long totalInputTokens = 0;
        long totalOutputTokens = 0;

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
                return SubAgentSessionResult.error("Error: " + e.getMessage());
            }
            messageParams.add(message.toParam());

            // 累积 token 使用情况
            if (message.usage() != null) {
                totalInputTokens += message.usage().inputTokens();
                totalOutputTokens += message.usage().outputTokens();
            }

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
                String result = message.content().stream()
                        .filter(ContentBlock::isText)
                        .map(cb -> cb.text().get().text())
                        .reduce("", (a, b) -> a + b);
                return SubAgentSessionResult.success(result, totalInputTokens, totalOutputTokens);
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
                                return SubAgentSessionResult.success(
                                        res.asString().get().toString(),
                                        totalInputTokens,
                                        totalOutputTokens);
                            }
                        }
                        return SubAgentSessionResult.success("Task completed.", totalInputTokens, totalOutputTokens);
                    } catch (Exception e) {
                        return SubAgentSessionResult.error("Error parsing task_completed: " + e.getMessage());
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
