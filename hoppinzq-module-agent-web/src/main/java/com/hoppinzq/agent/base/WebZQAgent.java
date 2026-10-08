package com.hoppinzq.agent.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.background.BackgroundManager;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import com.hoppinzq.agent.tool.task.TaskManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.hoppinzq.agent.constant.AIConstants.MAX_TOKENS;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;
import static com.hoppinzq.agent.constant.AIConstants.REACT_ENABLE;

/**
 * 智能体基类(Web版)。
 * <p>协议中立：阻塞 {@link #chatMessage(List)} 与流式 {@link #chatMessageStream(List, LlmProvider.StreamListener)}
 * 都经由 {@link LlmProvider}，协议差异（Anthropic / OpenAI）由 Provider 实现类封装。
 */
@Data
@Slf4j
public class WebZQAgent {
    private String systemPrompt;
    private final String model;
    protected final LlmProvider provider;
    protected final ConcurrentHashMap<String, List<LlmMessage>> sessionMessages = new ConcurrentHashMap<>();
    private List<ToolDefinition> tools;
    private String taskResult;
    private boolean taskCompleted = false;

    public static BackgroundManager backgroundManager;
    public static TaskManager taskManager;
    public static ContextCompactor compactor;
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;
    public static boolean manualCompactRequested = false;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;

    public WebZQAgent(LlmProvider provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    public WebZQAgent(LlmProvider provider, String model, List<ToolDefinition> tools) {
        this.provider = provider;
        this.model = model;
        this.tools = tools;
    }

    public void initManagers(BackgroundManager backgroundManager, TaskManager taskManager,
                             ContextCompactor compactor, SkillLoader skillLoader, TodoManager todoManager) {
        WebZQAgent.backgroundManager = backgroundManager;
        WebZQAgent.taskManager = taskManager;
        WebZQAgent.compactor = compactor;
        WebZQAgent.skillLoader = skillLoader;
        WebZQAgent.todoManager = todoManager;
        if (WebZQAgent.todoManager != null) {
            this.lastTodoVersion = WebZQAgent.todoManager.getVersion();
        }
    }

    public List<LlmMessage> getMessageParams(String sessionId) {
        return sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    public List<LlmMessage> getCurrentMessageParams() {
        String sessionId = com.hoppinzq.agent.context.SessionContextHolder.get();
        if (sessionId != null) {
            return getMessageParams(sessionId);
        }
        return sessionMessages.computeIfAbsent("default", k -> new ArrayList<>());
    }

    public String invokeTool(ToolDefinition tool, String arguments) throws Exception {
        JsonNode input = OBJECT_MAPPER.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        if (tool.getType() == null) {
            if (!input.isObject()) {
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数转换失败");
            }
            Map<String, Object> callTool = new HashMap<>();
            callTool.put("input", input);
            callTool.put("tool_name", tool.getName());
            return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(callTool));
        } else {
            Object pojo = OBJECT_MAPPER.treeToValue(input, tool.getType());
            return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(pojo));
        }
    }

    protected LlmResponse chatMessage(List<LlmMessage> messageParams) {
        compactIfNeeded(messageParams);
        return provider.complete(buildLlmRequest(messageParams));
    }

    /**
     * 流式补全：把协议流事件翻译为中立 {@link LlmProvider.StreamListener} 回调。
     */
    public void chatMessageStream(List<LlmMessage> messageParams, LlmProvider.StreamListener listener) {
        compactIfNeeded(messageParams);
        provider.stream(buildLlmRequest(messageParams), listener);
    }

    /** 注入后台通知 + 微压缩/自动压缩（阻塞与流式共用）。 */
    private void compactIfNeeded(List<LlmMessage> messageParams) {
        if (WebZQAgent.backgroundManager != null) {
            com.hoppinzq.agent.tool.background.BackgroundManager.injectBackgroundNotifications(messageParams, WebZQAgent.backgroundManager);
        }

        List<LlmMessage> compactedParams = messageParams;
        if (WebZQAgent.compactor != null) {
            compactedParams = WebZQAgent.compactor.microCompact(new ArrayList<>(messageParams));
            if (com.hoppinzq.agent.tool.compact.ContextCompactor.estimateTokens(compactedParams) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                log.info("[自动压缩已触发]");
                compactedParams = WebZQAgent.compactor.autoCompact(compactedParams);
                messageParams.clear();
                messageParams.addAll(compactedParams);
            }
        } else {
            messageParams.clear();
            messageParams.addAll(compactedParams);
        }
    }

    private LlmRequest buildLlmRequest(List<LlmMessage> messageParams) {
        LlmRequest.LlmRequestBuilder builder = LlmRequest.builder()
                .model(model)
                .messages(messageParams)
                .systemPrompt(systemPrompt)
                .maxTokens(MAX_TOKENS);
        if (!REACT_ENABLE && tools != null && !tools.isEmpty()) {
            builder.tools(tools);
        }
        return builder.build();
    }

    public String runTask(String prompt) {
        List<LlmMessage> currentMessages = getCurrentMessageParams();
        currentMessages.add(LlmMessage.user(prompt));

        while (true) {
            LlmResponse response;
            try {
                response = chatMessage(currentMessages);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                return "Error: 响应中没有消息";
            }

            if (REACT_ENABLE) {
                String resultText = message.getText() == null ? "" : message.getText();
                log.info("AI: {}", resultText);

                currentMessages.add(LlmMessage.assistant(resultText, null));

                if (resultText.contains("Action:")) {
                    String action = null;
                    java.util.regex.Pattern actionPattern = java.util.regex.Pattern.compile("Action:\\s*([^\\n]+)");
                    java.util.regex.Matcher actionMatcher = actionPattern.matcher(resultText);
                    if (actionMatcher.find()) {
                        action = actionMatcher.group(1).trim();
                    }

                    String actionInputStr = null;
                    java.util.regex.Pattern inputPattern = java.util.regex.Pattern.compile("Action Input:\\s*(\\{.*?\\})(?=\\s*\\n|$)", java.util.regex.Pattern.DOTALL);
                    java.util.regex.Matcher inputMatcher = inputPattern.matcher(resultText);
                    if (inputMatcher.find()) {
                        actionInputStr = inputMatcher.group(1);
                    }

                    if (action != null && actionInputStr != null) {
                        if ("task_completed".equals(action)) {
                            try {
                                Map<String, Object> inputMap = OBJECT_MAPPER.readValue(actionInputStr, Map.class);
                                Object res = inputMap.get("result");
                                if (res != null) {
                                    return res.toString();
                                }
                                return "Task completed.";
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
                            try {
                                observation = invokeTool(targetTool, actionInputStr);
                                log.info("Result: {}", observation);
                            } catch (Exception e) {
                                observation = "执行异常: " + e.getMessage();
                                log.error("Error: {}", e.getMessage(), e);
                            }
                        } else {
                            observation = "未知的工具: " + action;
                        }

                        List<LlmMessage> toolResults = new ArrayList<>();
                        toolResults.add(LlmMessage.user("Observation: " + observation));

                        onToolExecution(toolResults);

                        currentMessages.add(LlmMessage.user("Observation: " + observation));
                    } else {
                        return resultText;
                    }
                } else {
                    return resultText;
                }
            } else {
                currentMessages.add(message);

                List<LlmMessage> toolResults = new ArrayList<>();
                boolean hasToolUse = false;

                if (message.getText() != null && !message.getText().isBlank()) {
                    log.info("AI: {}", message.getText());
                }
                for (LlmMessage.ToolCall call : message.getToolCalls()) {
                    hasToolUse = true;
                    log.info("Tool: {}({})", call.getName(), call.getArgumentsJson());

                    if ("task_completed".equals(call.getName())) {
                        try {
                            JsonNode input = OBJECT_MAPPER.readTree(
                                    call.getArgumentsJson() == null || call.getArgumentsJson().isBlank()
                                            ? "{}" : call.getArgumentsJson());
                            JsonNode res = input.get("result");
                            if (res != null && res.isTextual()) {
                                return res.asText();
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
                        if (tool.getName().equals(call.getName())) {
                            try {
                                toolResult = invokeTool(tool, call.getArgumentsJson());
                                log.info("Result: {}", toolResult);
                            } catch (Exception e) {
                                toolError = e;
                                log.error("Error: {}", e.getMessage(), e);
                            }
                            toolFound = true;
                            break;
                        }
                    }

                    if (!toolFound) {
                        toolError = new Exception("工具 '" + call.getName() + "' 没有找到");
                        log.error("Error: {}", toolError.getMessage());
                    }

                    toolResults.add(LlmMessage.tool(call.getId(),
                            toolError != null ? "错误: " + toolError.getMessage() : toolResult));
                }

                if (!hasToolUse) {
                    // 无工具调用：文本回复即结果
                    return message.getText() == null ? "" : message.getText();
                }

                onToolExecution(toolResults);

                currentMessages.addAll(toolResults);
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
                String reminder = String.format("<reminder>\n您已经 %d 个回合没有更新待办事项列表了。请更新列表以反映当前进度。\n</reminder>", roundsSinceTodo);
                toolResults.add(LlmMessage.user(reminder));
            }
        }

        if (compactor != null) {
            List<LlmMessage> currentMessages = getCurrentMessageParams();
            compactor.microCompact(currentMessages);

            if (com.hoppinzq.agent.tool.compact.ContextCompactor.estimateTokens(currentMessages) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                log.info("[自动压缩已触发]");
                List<LlmMessage> compressed = compactor.autoCompact(currentMessages);
                currentMessages.clear();
                currentMessages.addAll(compressed);
            }

            if (manualCompactRequested) {
                log.info("[手动压缩]");
                List<LlmMessage> compressed = compactor.autoCompact(currentMessages);
                currentMessages.clear();
                currentMessages.addAll(compressed);
                manualCompactRequested = false;
            }
        }
    }
}
