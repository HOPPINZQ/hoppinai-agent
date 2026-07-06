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

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类
 * @author hoppinzq
 */
@Data
public class ZQAgent {
    private final String model;
    private final Scanner scanner;
    private final List<ToolDefinition> tools;
    protected final AnthropicClient client;
    private String systemPrompt;
    protected final List<MessageParam> messageParams = new ArrayList<>();
    private String taskResult;
    private boolean taskCompleted = false;
    /** 可选的会话管理器；设置后，每条消息会自动持久化，启动时自动恢复历史。 */
    private SessionManager sessionManager;
    /**
     * 可选的命令处理器；设置后可处理 /stats、/usage、/exit 等特殊命令。
     */
    private AgentCommandHandler commandHandler;

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
            // 每次 create 后都打印 assistant 的文本输出，避免纯文本回复（无工具调用）被静默吞掉
            printText(message);

            // 官方推荐的 canonical agentic loop：以 stop_reason == TOOL_USE 为循环条件
            // 见 https://platform.claude.com/docs/en/agents-and-tools/tool-use/how-tool-use-works
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
            // 非 TOOL_USE 退出：检查是否被截断
            warnIfTruncated(message);
        }
    }

    /**
     * 打印 assistant 消息中的所有文本块。空文本跳过。
     */
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

    /**
     * 判断是否需要继续工具循环。
     * <p>官方推荐以 {@code stop_reason == TOOL_USE} 而非遍历 content，
     * 因为模型可能输出空 tool_use 或被 max_tokens 截断，stop_reason 更可靠。
     */
    private boolean isToolUse(Message message) {
        return message.stopReason()
                .map(StopReason.TOOL_USE::equals)
                .orElse(false);
    }

    /** 命中 MAX_TOKENS 时打印警告，避免静默截断工具调用导致死循环。 */
    private void warnIfTruncated(Message message) {
        boolean maxTokens = message.stopReason()
                .map(StopReason.MAX_TOKENS::equals)
                .orElse(false);
        if (maxTokens) {
            System.out.printf("\u001b[91m[警告]\u001b[0m 本轮回复被 max_tokens=%d 截断，工具调用可能不完整。建议调大 MAX_TOKENS。%n",
                    MAX_TOKENS);
        }
    }

    /**
     * 执行一轮 assistant 回复中的所有工具调用，返回封装好的 user 角色 tool_result 消息。
     * <p>文本块已由 {@link #printText(Message)} 处理，这里只负责工具；
     * 找不到工具或执行抛异常都会被标记为 isError=true 回灌给模型。
     */
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

    /**
     * 工具执行后的回调钩子，子类可重写以注入额外内容（如提醒、压缩等）。
     * 注意：基类的 {@link #executeToolCalls(Message)} 不再调用此方法；
     * 仅为兼容已有子类重写而保留。
     */
    protected void onToolExecution(List<ContentBlockParam> toolResults) {
        // 默认空实现
    }

    private String invokeTool(ToolDefinition tool, JsonValue input) {
        if (tool.getType() == null) {
            if (input.asObject().isEmpty()) {
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数不是 JSON 对象");
            }
            // 把 JsonValue 落到 Jackson JsonNode 后再组装，避免直接序列化 SDK 内部包装类型
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.set("input", input.convert(JsonNode.class));
            root.put("tool_name", tool.getName());
            return tool.getFunction().apply(root.toString());
        } else {
            // 工具自定义了入参 POJO 类型：依赖其 toString() 返回 JSON
            return tool.getFunction().apply(Objects.requireNonNull(input.convert(tool.getType())).toString());
        }
    }

    protected Message chatMessage(List<MessageParam> messageParams) {
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

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
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

            List<ContentBlockParam> toolResults = new ArrayList<>();
            boolean hasToolUse = false;

            for (ContentBlock content : message.content()) {
                if (content.isText()) {
                    Optional<TextBlock> text = content.text();
                    String result = text.map(TextBlock::text).orElse("");
                    System.out.printf("\u001b[94m（子）AI\u001b[0m: %s%n", result);
                } else if (content.isToolUse()) {
                    hasToolUse = true;
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
                    boolean toolFound = false;

                    for (ToolDefinition tool : tools) {
                        if (tool.getName().equals(toolUse.name())) {
                            try {
                                JsonValue input = toolUse._input();
                                toolResult = invokeTool(tool, input);
                                System.out.printf("\u001b[92m（子）结果\u001b[0m: %s%n", toolResult);
                            } catch (Exception e) {
                                toolError = e;
                                System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", e.getMessage());
                                e.printStackTrace();
                            }
                            toolFound = true;
                            break;
                        }
                    }

                    if (!toolFound) {
                        toolError = new Exception("工具 '" + toolUse.name() + "' 没有找到");
                        System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", toolError.getMessage());
                    }

                    if (toolError != null) {
                        toolResults.add(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUse.id())
                                        .content(toolError.getMessage())
                                        .isError(true)
                                        .build()
                        ));
                    } else {
                        toolResults.add(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUse.id())
                                        .content(toolResult)
                                        .isError(false)
                                        .build()
                        ));
                    }
                }
            }

            if (!hasToolUse) {
                // Return the text response as result if no tools used
                return message.content().stream()
                        .filter(ContentBlock::isText)
                        .map(cb -> cb.text().get().text())
                        .reduce("", (a, b) -> a + b);
            }
            MessageParam.Content content = MessageParam.Content.ofBlockParams(toolResults);
            MessageParam toolResultMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(content)
                    .build();
            messageParams.add(toolResultMessage);
        }
    }

    /**
     * 执行任务并返回包含 token 使用情况的结果
     *
     * @param prompt 任务提示词
     * @return 包含结果和 token 使用情况的 SubAgentSessionResult
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
