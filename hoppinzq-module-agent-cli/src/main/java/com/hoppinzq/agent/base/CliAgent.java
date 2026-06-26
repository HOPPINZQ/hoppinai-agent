package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.cli.CliRenderer;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.background.BackgroundManager;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import com.hoppinzq.agent.tool.task.TaskManager;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.MAX_TOKENS;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;
import static com.hoppinzq.agent.constant.AIConstants.REACT_ENABLE;
import static com.hoppinzq.agent.constant.AIConstants.TEMPERATURE;

/**
 * CLI 智能体基类（单会话）。
 * <p>
 * 与 web 模块的 {@code WebZQAgent} 区别：
 * <ul>
 *   <li>单会话：{@code messageParams} 是普通 {@link ArrayList}，不用 {@code ConcurrentHashMap}。</li>
 *   <li>不依赖 SessionContextHolder：{@link #getCurrentMessageParams()} 直接返回实例字段。</li>
 *   <li>所有输出经 {@link CliRenderer} 的 box-drawing 面板渲染（思考 / 工具调用 / 返回 / 回复 / 错误 / token 统计）。</li>
 *   <li>工具调用前后用 {@link System#nanoTime()} 测耗时。</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
public class CliAgent {
    private String systemPrompt;
    private final String model;
    protected final AnthropicClient client;
    /** 单会话消息列表。 */
    protected final List<MessageParam> messageParams = new ArrayList<>();
    private List<ToolDefinition> tools;
    private String taskResult;
    private boolean taskCompleted = false;

    /** 静态 manager 字段。给默认初始值，避免 ToolDefinition 类加载时 NPE。{@code initManagers} 可覆盖。 */
    public static BackgroundManager backgroundManager = new BackgroundManager();
    public static TaskManager taskManager = new TaskManager();
    public static ContextCompactor compactor;
    public static SkillLoader skillLoader = new SkillLoader();
    public static TodoManager todoManager = new TodoManager();
    public static boolean manualCompactRequested = false;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;

    public CliAgent(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    public CliAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
        this.model = model;
        this.tools = tools;
    }

    public void initManagers(BackgroundManager backgroundManager, TaskManager taskManager,
                             ContextCompactor compactor, SkillLoader skillLoader, TodoManager todoManager) {
        CliAgent.backgroundManager = backgroundManager;
        CliAgent.taskManager = taskManager;
        CliAgent.compactor = compactor;
        CliAgent.skillLoader = skillLoader;
        CliAgent.todoManager = todoManager;
        if (CliAgent.todoManager != null) {
            this.lastTodoVersion = CliAgent.todoManager.getVersion();
        }
    }

    /** 单会话：直接返回实例字段。 */
    public List<MessageParam> getCurrentMessageParams() {
        return messageParams;
    }

    public String invokeTool(ToolDefinition tool, JsonValue input) throws Exception {
        if (tool.getType() == null) {
            Optional<Map<String, JsonValue>> object = input.asObject();
            if (object.isPresent()) {
                Map<String, JsonValue> map = object.get();
                Map<String, Object> callTool = new HashMap<>();
                callTool.put("input", map);
                callTool.put("tool_name", tool.getName());
                return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(callTool));
            } else {
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数转换失败");
            }
        } else {
            return tool.getFunction().apply(Objects.requireNonNull(input.convert(tool.getType())).toString());
        }
    }

    protected Message chatMessage(List<MessageParam> messageParams) {
        if (CliAgent.backgroundManager != null) {
            BackgroundManager.injectBackgroundNotifications(messageParams, CliAgent.backgroundManager);
        }

        List<MessageParam> compactedParams = messageParams;
        if (CliAgent.compactor != null) {
            compactedParams = CliAgent.compactor.microCompact(new ArrayList<>(messageParams));
            if (ContextCompactor.estimateTokens(compactedParams) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                CliRenderer.thinking("[自动压缩已触发]");
                compactedParams = CliAgent.compactor.autoCompact(compactedParams);
                messageParams.clear();
                messageParams.addAll(compactedParams);
            }
        }

        MessageCreateParams params = buildMessageParams(compactedParams);
        Message message = client.messages().create(params);
        // token 统计
        try {
            Usage usage = message.usage();
            if (usage != null) {
                CliRenderer.stats(usage.inputTokens(), usage.outputTokens());
            }
        } catch (Exception ignored) {
            // 某些代理可能不返回 usage，忽略
        }
        return message;
    }

    /** 主循环（命令行）。 */
    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("开始对话吧（输入 '退出' 结束）");
        while (true) {
            System.out.print(CliRenderer.BLUE + CliRenderer.BOLD + "你" + CliRenderer.RESET + ": ");
            String userInput;
            try {
                userInput = scanner.nextLine();
            } catch (NoSuchElementException | IllegalStateException e) {
                // Ctrl+D / 关闭输入
                break;
            }
            if (userInput == null || userInput.isEmpty()) {
                continue;
            }
            if ("退出".equals(userInput.trim()) || "exit".equalsIgnoreCase(userInput.trim())
                    || "quit".equalsIgnoreCase(userInput.trim())) {
                System.out.println("再见 👋");
                break;
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
                CliRenderer.error(e.getMessage() == null ? e.toString() : e.getMessage());
                continue;
            }
            messageParams.add(message.toParam());

            // 工具循环
            while (true) {
                // —— ReAct 模式：模型用文本输出 "Action:" / "Action Input:" ——
                if (REACT_ENABLE) {
                    String fullText = message.content().stream()
                            .filter(ContentBlock::isText)
                            .map(cb -> cb.text().get().text())
                            .reduce("", (a, b) -> a + b);
                    if (fullText.contains("Action:")) {
                        String action = extractFirst(fullText, "Action:\\s*([^\\n]+)");
                        String actionInputStr = extractFirst(fullText,
                                "Action Input:\\s*(\\{.*?\\})(?=\\s*\\n|$)");

                        if (action != null && actionInputStr != null) {
                            // 外层已 add message.toParam()，这里不再重复入栈
                            splitAndRender(fullText);

                            long startNs = System.nanoTime();
                            String observation;
                            Exception err = null;
                            ToolDefinition targetTool = null;
                            for (ToolDefinition tool : tools) {
                                if (tool.getName().equals(action)) {
                                    targetTool = tool;
                                    break;
                                }
                            }
                            if (targetTool == null) {
                                observation = "未知的工具: " + action;
                            } else {
                                try {
                                    Map<String, Object> inputMap = OBJECT_MAPPER.readValue(actionInputStr, Map.class);
                                    observation = invokeTool(targetTool, JsonValue.from(inputMap));
                                } catch (Exception e) {
                                    err = e;
                                    observation = "执行异常: " + e.getMessage();
                                }
                            }
                            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                            CliRenderer.toolCall(action, actionInputStr, observation, elapsedMs);
                            if (err != null) {
                                CliRenderer.error(err.getMessage() == null ? err.toString() : err.getMessage());
                            }

                            List<ContentBlockParam> reactResults = new ArrayList<>();
                            reactResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                                    .text(observation).build()));
                            onToolExecution(reactResults);

                            messageParams.add(MessageParam.builder()
                                    .role(MessageParam.Role.USER)
                                    .content("Observation: " + observation)
                                    .build());
                            try {
                                message = chatMessage(messageParams);
                            } catch (Exception e) {
                                CliRenderer.error(e.getMessage() == null ? e.toString() : e.getMessage());
                                break;
                            }
                            messageParams.add(message.toParam());
                            continue;
                        }
                        // Action 不完整：当作最终回复
                        break;
                    }
                    // 没有 Action: 当作最终回复
                    for (ContentBlock content : message.content()) {
                        if (content.isText()) {
                            splitAndRender(content.text().map(TextBlock::text).orElse(""));
                        }
                    }
                    break;
                }

                // —— 非 ReAct：原生 tool_use block 通道 ——
                List<ContentBlockParam> toolResults = new ArrayList<>();
                boolean hasToolUse = false;

                for (ContentBlock content : message.content()) {
                    if (content.isText()) {
                        String text = content.text().map(TextBlock::text).orElse("");
                        splitAndRender(text);
                    } else if (content.isToolUse()) {
                        hasToolUse = true;
                        ToolUseBlock toolUse = content.asToolUse();

                        String toolResult = null;
                        Exception toolError = null;
                        boolean toolFound = false;
                        long startNs = System.nanoTime();
                        String inputRepr = String.valueOf(toolUse._input());

                        for (ToolDefinition tool : tools) {
                            if (tool.getName().equals(toolUse.name())) {
                                try {
                                    toolResult = invokeTool(tool, toolUse._input());
                                } catch (Exception e) {
                                    toolError = e;
                                }
                                toolFound = true;
                                break;
                            }
                        }

                        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                        if (!toolFound) {
                            toolError = new Exception("工具 '" + toolUse.name() + "' 没有找到");
                        }

                        if (toolError != null) {
                            CliRenderer.toolCall(toolUse.name(), inputRepr, "错误: " + toolError.getMessage(), elapsedMs);
                            CliRenderer.error(toolError.getMessage() == null ? toolError.toString() : toolError.getMessage());
                            toolResults.add(ContentBlockParam.ofToolResult(
                                    ToolResultBlockParam.builder()
                                            .toolUseId(toolUse.id())
                                            .content(toolError.getMessage())
                                            .isError(true)
                                            .build()
                            ));
                        } else {
                            CliRenderer.toolCall(toolUse.name(), inputRepr, toolResult, elapsedMs);
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
                    break;
                }

                onToolExecution(toolResults);

                MessageParam.Content content = MessageParam.Content.ofBlockParams(toolResults);
                MessageParam toolResultMessage = MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(content)
                        .build();
                messageParams.add(toolResultMessage);
                try {
                    message = chatMessage(messageParams);
                } catch (Exception e) {
                    CliRenderer.error(e.getMessage() == null ? e.toString() : e.getMessage());
                    break;
                }
                messageParams.add(message.toParam());
            }
        }
    }

    /**
     * 处理 ReAct 模式（或单 prompt 任务）。供 {@link SubAgent} 复用。
     */
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

            if (REACT_ENABLE) {
                String resultText = "";
                for (ContentBlock content : message.content()) {
                    if (content.isText()) {
                        resultText += content.text().map(TextBlock::text).orElse("");
                    }
                }
                messageParams.add(MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .content(resultText)
                        .build());
                splitAndRender(resultText);

                if (resultText.contains("Action:")) {
                    String action = extractFirst(resultText, "Action:\\s*([^\\n]+)");
                    String actionInputStr = extractFirst(resultText, "Action Input:\\s*(\\{.*?\\})(?=\\s*\\n|$)");

                    if (action != null && actionInputStr != null) {
                        if ("task_completed".equals(action)) {
                            try {
                                Map<String, Object> inputMap = OBJECT_MAPPER.readValue(actionInputStr, Map.class);
                                Object res = inputMap.get("result");
                                return res != null ? res.toString() : "Task completed.";
                            } catch (Exception e) {
                                return "Error parsing task_completed: " + e.getMessage();
                            }
                        }

                        ToolDefinition targetTool = null;
                        for (ToolDefinition tool : tools) {
                            if (tool.getName().equals(action)) {
                                targetTool = tool;
                                break;
                            }
                        }

                        String observation;
                        if (targetTool != null) {
                            long startNs = System.nanoTime();
                            try {
                                Map<String, Object> inputMap = OBJECT_MAPPER.readValue(actionInputStr, Map.class);
                                JsonValue inputJson = JsonValue.from(inputMap);
                                observation = invokeTool(targetTool, inputJson);
                                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                                CliRenderer.toolCall(action, actionInputStr, observation, elapsedMs);
                            } catch (Exception e) {
                                observation = "执行异常: " + e.getMessage();
                                CliRenderer.error(e.getMessage() == null ? e.toString() : e.getMessage());
                            }
                        } else {
                            observation = "未知的工具: " + action;
                        }

                        List<ContentBlockParam> toolResults = new ArrayList<>();
                        toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(observation)
                                .build()));
                        onToolExecution(toolResults);

                        messageParams.add(MessageParam.builder()
                                .role(MessageParam.Role.USER)
                                .content("Observation: " + observation)
                                .build());
                    } else {
                        return resultText;
                    }
                } else {
                    return resultText;
                }
            } else {
                messageParams.add(message.toParam());

                List<ContentBlockParam> toolResults = new ArrayList<>();
                boolean hasToolUse = false;

                for (ContentBlock content : message.content()) {
                    if (content.isText()) {
                        String result = content.text().map(TextBlock::text).orElse("");
                        splitAndRender(result);
                    } else if (content.isToolUse()) {
                        hasToolUse = true;
                        ToolUseBlock toolUse = content.asToolUse();

                        String toolResult = null;
                        Exception toolError = null;
                        boolean toolFound = false;
                        long startNs = System.nanoTime();
                        String inputRepr = String.valueOf(toolUse._input());

                        for (ToolDefinition tool : tools) {
                            if (tool.getName().equals(toolUse.name())) {
                                try {
                                    toolResult = invokeTool(tool, toolUse._input());
                                } catch (Exception e) {
                                    toolError = e;
                                }
                                toolFound = true;
                                break;
                            }
                        }

                        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                        if (!toolFound) {
                            toolError = new Exception("工具 '" + toolUse.name() + "' 没有找到");
                        }

                        if (toolError != null) {
                            CliRenderer.toolCall(toolUse.name(), inputRepr, "错误: " + toolError.getMessage(), elapsedMs);
                            toolResults.add(ContentBlockParam.ofToolResult(
                                    ToolResultBlockParam.builder()
                                            .toolUseId(toolUse.id())
                                            .content(toolError.getMessage())
                                            .isError(true)
                                            .build()
                            ));
                        } else {
                            CliRenderer.toolCall(toolUse.name(), inputRepr, toolResult, elapsedMs);
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
                    return message.content().stream()
                            .filter(ContentBlock::isText)
                            .map(cb -> cb.text().get().text())
                            .reduce("", (a, b) -> a + b);
                }

                onToolExecution(toolResults);

                MessageParam.Content content = MessageParam.Content.ofBlockParams(toolResults);
                MessageParam toolResultMessage = MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(content)
                        .build();
                messageParams.add(toolResultMessage);
            }
        }
    }

    public void onToolExecution(List<ContentBlockParam> toolResults) {
        if (todoManager != null) {
            long currentVersion = todoManager.getVersion();
            if (currentVersion > lastTodoVersion) {
                roundsSinceTodo = 0;
                lastTodoVersion = currentVersion;
            } else {
                roundsSinceTodo++;
            }
            if (roundsSinceTodo >= 3) {
                String reminder = String.format(
                        "<reminder>\n您已经 %d 个回合没有更新待办事项列表了。请更新列表以反映当前进度。\n</reminder>",
                        roundsSinceTodo);
                toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                        .text(reminder)
                        .build()));
            }
        }

        if (compactor != null) {
            List<MessageParam> currentMessages = getCurrentMessageParams();
            compactor.microCompact(currentMessages);
            if (ContextCompactor.estimateTokens(currentMessages) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                CliRenderer.thinking("[自动压缩已触发]");
                List<MessageParam> compressed = compactor.autoCompact(currentMessages);
                currentMessages.clear();
                currentMessages.addAll(compressed);
            }
            if (manualCompactRequested) {
                CliRenderer.thinking("[手动压缩]");
                List<MessageParam> compressed = compactor.autoCompact(currentMessages);
                currentMessages.clear();
                currentMessages.addAll(compressed);
                manualCompactRequested = false;
            }
        }
    }

    private MessageCreateParams buildMessageParams(List<MessageParam> messageParams) {
        MessageCreateParams.Builder messageBuilder = MessageCreateParams.builder()
                .model(model)
                .messages(messageParams);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messageBuilder.system(systemPrompt);
        }
        if (!REACT_ENABLE && tools != null && !tools.isEmpty()) {
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
            messageBuilder.tools(anthropicTools);
        }
        messageBuilder.maxTokens(MAX_TOKENS);
        messageBuilder.temperature(TEMPERATURE);
        return messageBuilder.build();
    }

    /**
     * 把 AI 文本回复按"思考前缀"分离：以"让我"、"我需要"、"让我想想"、"思考"、"首先"等开头的段落
     * 用灰色 dim 面板渲染，其余作为正式回复用 aiReply 面板。
     */
    private void splitAndRender(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        // 简单策略：按段落（双换行）切分，命中思考前缀的归为 thinking
        String[] paragraphs = text.split("(?m)^\\s*$", 2);
        String thinkPrefixRegex = "^(让我|我需要|让我想|思考|首先|嗯|好的|OK|让我考虑).*$";
        if (paragraphs.length == 2 && paragraphs[0].trim().matches("(?s)" + thinkPrefixRegex)) {
            CliRenderer.thinking(paragraphs[0].trim());
            CliRenderer.aiReply(paragraphs[1].trim());
        } else {
            CliRenderer.aiReply(text.trim());
        }
    }

    private static String extractFirst(String text, String regex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
}
