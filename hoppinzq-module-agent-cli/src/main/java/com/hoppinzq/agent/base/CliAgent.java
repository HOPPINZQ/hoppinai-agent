package com.hoppinzq.agent.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.cli.CliRenderer;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.session.TokenUsage;
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
 * <p>只面向协议中立的 {@link LlmProvider}/{@link LlmMessage} 编程，
 * 协议差异（Anthropic / OpenAI）由 Provider 实现类封装。
 *
 * @author hoppinzq
 */
@Data
public class CliAgent {
    private String systemPrompt;
    private final String model;
    protected final LlmProvider provider;
    /** 单会话消息列表。 */
    protected final List<LlmMessage> messageParams = new ArrayList<>();
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

    public CliAgent(LlmProvider provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    public CliAgent(LlmProvider provider, String model, List<ToolDefinition> tools) {
        this.provider = provider;
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
    public List<LlmMessage> getCurrentMessageParams() {
        return messageParams;
    }

    public String invokeTool(ToolDefinition tool, String arguments) throws Exception {
        JsonNode input = OBJECT_MAPPER.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        if (tool.getType() == null) {
            if (!input.isObject()) {
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数不是 JSON 对象");
            }
            // 保持旧的包装格式：{"input": {...}, "tool_name": "..."}
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.set("input", input);
            root.put("tool_name", tool.getName());
            return tool.getFunction().apply(root.toString());
        } else {
            // 工具自定义了入参 POJO 类型：先规整化再序列化，与旧实现行为一致
            Object pojo = OBJECT_MAPPER.treeToValue(input, tool.getType());
            return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(pojo));
        }
    }

    protected LlmResponse chatMessage(List<LlmMessage> messageParams) {
        if (CliAgent.backgroundManager != null) {
            BackgroundManager.injectBackgroundNotifications(messageParams, CliAgent.backgroundManager);
        }

        List<LlmMessage> compactedParams = messageParams;
        if (CliAgent.compactor != null) {
            compactedParams = CliAgent.compactor.microCompact(new ArrayList<>(messageParams));
            if (CliAgent.compactor.countTokens(compactedParams) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                CliRenderer.thinking("[自动压缩已触发]");
                compactedParams = CliAgent.compactor.autoCompact(compactedParams);
                messageParams.clear();
                messageParams.addAll(compactedParams);
            }
        }

        LlmRequest.LlmRequestBuilder requestBuilder = LlmRequest.builder()
                .model(model)
                .systemPrompt(systemPrompt)
                .messages(compactedParams)
                .maxTokens(MAX_TOKENS);
        // ReAct 文本协议下不传工具 schema，工具调用走 Action:/Action Input: 文本解析
        if (!REACT_ENABLE && tools != null && !tools.isEmpty()) {
            requestBuilder.tools(tools);
        }
        LlmResponse response = provider.complete(requestBuilder.build());
        // token 统计
        try {
            TokenUsage usage = response.getUsage();
            if (usage != null) {
                CliRenderer.stats(usage.getInputTokens(), usage.getOutputTokens());
            }
        } catch (Exception ignored) {
            // 某些代理可能不返回 usage，忽略
        }
        return response;
    }

    /**
     * 判断是否需要继续工具循环。
     * <p>以 {@code finish_reason == tool_calls} 为主判据；个别 OpenAI 兼容后端
     * 返回 tool_calls 时 finish_reason 可能仍是 stop，故兜底检查 toolCalls 非空。
     */
    private boolean hasPendingToolCalls(LlmResponse response) {
        if (response.getFinishReason() == LlmResponse.FinishReason.TOOL_CALLS) {
            return true;
        }
        LlmMessage message = response.getMessage();
        return message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
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

            messageParams.add(LlmMessage.user(userInput));

            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                CliRenderer.error(e.getMessage() == null ? e.toString() : e.getMessage());
                continue;
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                CliRenderer.error("响应中没有消息");
                continue;
            }
            messageParams.add(message);

            // 工具循环
            while (true) {
                // —— ReAct 模式：模型用文本输出 "Action:" / "Action Input:" ——
                if (REACT_ENABLE) {
                    String fullText = message.getText() == null ? "" : message.getText();
                    if (fullText.contains("Action:")) {
                        String action = extractFirst(fullText, "Action:\\s*([^\\n]+)");
                        String actionInputStr = extractFirst(fullText,
                                "Action Input:\\s*(\\{.*?\\})(?=\\s*\\n|$)");

                        if (action != null && actionInputStr != null) {
                            // 外层已 add message，这里不再重复入栈
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
                                    observation = invokeTool(targetTool, actionInputStr);
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

                            List<LlmMessage> reactResults = new ArrayList<>();
                            reactResults.add(LlmMessage.user(observation));
                            onToolExecution(reactResults);

                            messageParams.add(LlmMessage.user("Observation: " + observation));
                            try {
                                response = chatMessage(messageParams);
                            } catch (Exception e) {
                                CliRenderer.error(e.getMessage() == null ? e.toString() : e.getMessage());
                                break;
                            }
                            message = response.getMessage();
                            if (message == null) {
                                CliRenderer.error("响应中没有消息");
                                break;
                            }
                            messageParams.add(message);
                            continue;
                        }
                        // Action 不完整：当作最终回复
                        break;
                    }
                    // 没有 Action: 当作最终回复
                    if (!fullText.isEmpty()) {
                        splitAndRender(fullText);
                    }
                    break;
                }

                // —— 非 ReAct：原生 tool_calls 通道 ——
                List<LlmMessage> toolResults = new ArrayList<>();
                boolean hasToolUse = hasPendingToolCalls(response);

                String text = message.getText();
                if (text != null && !text.isEmpty()) {
                    splitAndRender(text);
                }

                for (LlmMessage.ToolCall toolUse : message.getToolCalls()) {
                    String toolResult = null;
                    Exception toolError = null;
                    boolean toolFound = false;
                    long startNs = System.nanoTime();
                    String inputRepr = String.valueOf(toolUse.getArgumentsJson());

                    for (ToolDefinition tool : tools) {
                        if (tool.getName().equals(toolUse.getName())) {
                            try {
                                toolResult = invokeTool(tool, toolUse.getArgumentsJson());
                            } catch (Exception e) {
                                toolError = e;
                            }
                            toolFound = true;
                            break;
                        }
                    }

                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                    if (!toolFound) {
                        toolError = new Exception("工具 '" + toolUse.getName() + "' 没有找到");
                    }

                    if (toolError != null) {
                        CliRenderer.toolCall(toolUse.getName(), inputRepr, "错误: " + toolError.getMessage(), elapsedMs);
                        CliRenderer.error(toolError.getMessage() == null ? toolError.toString() : toolError.getMessage());
                        toolResults.add(LlmMessage.tool(toolUse.getId(), "错误: " + toolError.getMessage()));
                    } else {
                        CliRenderer.toolCall(toolUse.getName(), inputRepr, toolResult, elapsedMs);
                        toolResults.add(LlmMessage.tool(toolUse.getId(), toolResult));
                    }
                }

                if (!hasToolUse) {
                    break;
                }

                onToolExecution(toolResults);

                messageParams.addAll(toolResults);
                try {
                    response = chatMessage(messageParams);
                } catch (Exception e) {
                    CliRenderer.error(e.getMessage() == null ? e.toString() : e.getMessage());
                    break;
                }
                message = response.getMessage();
                if (message == null) {
                    CliRenderer.error("响应中没有消息");
                    break;
                }
                messageParams.add(message);
            }
        }
    }

    /**
     * 处理 ReAct 模式（或单 prompt 任务）。供 {@link SubAgent} 复用。
     */
    public String runTask(String prompt) {
        messageParams.add(LlmMessage.user(prompt));

        while (true) {
            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                return "Error: 响应中没有消息";
            }

            if (REACT_ENABLE) {
                String resultText = message.getText() == null ? "" : message.getText();
                messageParams.add(LlmMessage.assistant(resultText, null));
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
                                observation = invokeTool(targetTool, actionInputStr);
                                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                                CliRenderer.toolCall(action, actionInputStr, observation, elapsedMs);
                            } catch (Exception e) {
                                observation = "执行异常: " + e.getMessage();
                                CliRenderer.error(e.getMessage() == null ? e.toString() : e.getMessage());
                            }
                        } else {
                            observation = "未知的工具: " + action;
                        }

                        List<LlmMessage> toolResults = new ArrayList<>();
                        toolResults.add(LlmMessage.user(observation));
                        onToolExecution(toolResults);

                        messageParams.add(LlmMessage.user("Observation: " + observation));
                    } else {
                        return resultText;
                    }
                } else {
                    return resultText;
                }
            } else {
                messageParams.add(message);

                List<LlmMessage> toolResults = new ArrayList<>();
                boolean hasToolUse = hasPendingToolCalls(response);

                String result = message.getText();
                if (result != null && !result.isEmpty()) {
                    splitAndRender(result);
                }

                for (LlmMessage.ToolCall toolUse : message.getToolCalls()) {
                    String toolResult = null;
                    Exception toolError = null;
                    boolean toolFound = false;
                    long startNs = System.nanoTime();
                    String inputRepr = String.valueOf(toolUse.getArgumentsJson());

                    for (ToolDefinition tool : tools) {
                        if (tool.getName().equals(toolUse.getName())) {
                            try {
                                toolResult = invokeTool(tool, toolUse.getArgumentsJson());
                            } catch (Exception e) {
                                toolError = e;
                            }
                            toolFound = true;
                            break;
                        }
                    }

                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                    if (!toolFound) {
                        toolError = new Exception("工具 '" + toolUse.getName() + "' 没有找到");
                    }

                    if (toolError != null) {
                        CliRenderer.toolCall(toolUse.getName(), inputRepr, "错误: " + toolError.getMessage(), elapsedMs);
                        toolResults.add(LlmMessage.tool(toolUse.getId(), "错误: " + toolError.getMessage()));
                    } else {
                        CliRenderer.toolCall(toolUse.getName(), inputRepr, toolResult, elapsedMs);
                        toolResults.add(LlmMessage.tool(toolUse.getId(), toolResult));
                    }
                }

                if (!hasToolUse) {
                    return message.getText() == null ? "" : message.getText();
                }

                onToolExecution(toolResults);

                messageParams.addAll(toolResults);
            }
        }
    }

    public void onToolExecution(List<LlmMessage> toolResults) {
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
                toolResults.add(LlmMessage.user(reminder));
            }
        }

        if (compactor != null) {
            List<LlmMessage> currentMessages = getCurrentMessageParams();
            compactor.microCompact(currentMessages);
            if (compactor.countTokens(currentMessages) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                CliRenderer.thinking("[自动压缩已触发]");
                List<LlmMessage> compressed = compactor.autoCompact(currentMessages);
                currentMessages.clear();
                currentMessages.addAll(compressed);
            }
            if (manualCompactRequested) {
                CliRenderer.thinking("[手动压缩]");
                List<LlmMessage> compressed = compactor.autoCompact(currentMessages);
                currentMessages.clear();
                currentMessages.addAll(compressed);
                manualCompactRequested = false;
            }
        }
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
