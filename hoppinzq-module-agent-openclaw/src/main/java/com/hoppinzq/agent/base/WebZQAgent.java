package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.*;
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
import static com.hoppinzq.agent.constant.AIConstants.TEMPERATURE;
import static com.hoppinzq.agent.constant.AIConstants.REACT_ENABLE;

/**
 * 智能体基类(Web版)
 */
@Data
@Slf4j
public class WebZQAgent {
    private String systemPrompt;
    private final String model;
    protected final AnthropicClient client;
    protected final ConcurrentHashMap<String, List<MessageParam>> sessionMessages = new ConcurrentHashMap<>();
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

    public WebZQAgent(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    public WebZQAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
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

    public List<MessageParam> getMessageParams(String sessionId) {
        return sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    public List<MessageParam> getCurrentMessageParams() {
        String sessionId = com.hoppinzq.agent.context.SessionContextHolder.get();
        if (sessionId != null) {
            return getMessageParams(sessionId);
        }
        return sessionMessages.computeIfAbsent("default", k -> new ArrayList<>());
    }

    public String invokeTool(ToolDefinition tool, JsonValue input) throws Exception {
        if(tool.getType() == null){
            Optional<Map<String, JsonValue>> object = input.asObject();
            if(object.isPresent()){
                Map<String, JsonValue> map = object.get();
                Map<String, Object> callTool = new HashMap<>();
                callTool.put("input",map);
                callTool.put("tool_name",tool.getName());
                return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(callTool));
            }else{
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数转换失败");
            }
        }else{
            return tool.getFunction().apply(Objects.requireNonNull(input.convert(tool.getType())).toString());
        }
    }

    protected Message chatMessage(List<MessageParam> messageParams) {
        if (WebZQAgent.backgroundManager != null) {
            com.hoppinzq.agent.tool.background.BackgroundManager.injectBackgroundNotifications(messageParams, WebZQAgent.backgroundManager);
        }
        
        List<MessageParam> compactedParams = messageParams;
        if (WebZQAgent.compactor != null) {
            compactedParams = WebZQAgent.compactor.microCompact(new ArrayList<>(messageParams));
            if (com.hoppinzq.agent.tool.compact.ContextCompactor.estimateTokens(compactedParams) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                log.info("[自动压缩已触发]");
                compactedParams = WebZQAgent.compactor.autoCompact(compactedParams);
                messageParams.clear();
                messageParams.addAll(compactedParams);
            }
        }
        
        MessageCreateParams params = buildMessageParams(compactedParams);
        return client.messages().create(params);
    }

    public StreamResponse<RawMessageStreamEvent> chatMessageStream(List<MessageParam> messageParams) {
        if (WebZQAgent.backgroundManager != null) {
            com.hoppinzq.agent.tool.background.BackgroundManager.injectBackgroundNotifications(messageParams, this.backgroundManager);
        }
        
        List<MessageParam> compactedParams = messageParams;
        if (WebZQAgent.compactor != null) {
            compactedParams = WebZQAgent.compactor.microCompact(new ArrayList<>(messageParams));
            if (com.hoppinzq.agent.tool.compact.ContextCompactor.estimateTokens(compactedParams) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                log.info("[自动压缩已触发]");
                compactedParams = WebZQAgent.compactor.autoCompact(compactedParams);
                messageParams.clear();
                messageParams.addAll(compactedParams);
            }
        }
        
        MessageCreateParams params = buildMessageParams(compactedParams);
        return client.messages().createStreaming(params);
    }

    public String runTask(String prompt) {
        List<MessageParam> currentMessages = getCurrentMessageParams();
        MessageParam userMessage = MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(prompt)
                .build();
        currentMessages.add(userMessage);

        while (true) {
            Message message;
            try {
                message = chatMessage(currentMessages);
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

                currentMessages.add(MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .content(resultText)
                        .build());

                log.info("AI: {}", resultText);

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
                                Map<String, Object> inputMap = OBJECT_MAPPER.readValue(actionInputStr, Map.class);
                                JsonValue inputJson = JsonValue.from(inputMap);
                                observation = invokeTool(targetTool, inputJson);
                                log.info("Result: {}", observation);
                            } catch (Exception e) {
                                observation = "执行异常: " + e.getMessage();
                                log.error("Error: {}", e.getMessage(), e);
                            }
                        } else {
                            observation = "未知的工具: " + action;
                        }

                        List<ContentBlockParam> toolResults = new ArrayList<>();
                        toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                                .text(observation)
                                .build()));
                        
                        onToolExecution(toolResults);

                        currentMessages.add(MessageParam.builder()
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
                currentMessages.add(message.toParam());

                List<ContentBlockParam> toolResults = new ArrayList<>();
                boolean hasToolUse = false;

                for (ContentBlock content : message.content()) {
                    if (content.isText()) {
                        Optional<TextBlock> text = content.text();
                        String result = text.map(TextBlock::text).orElse("");
                        log.info("AI: {}", result);
                    } else if (content.isToolUse()) {
                        hasToolUse = true;
                        ToolUseBlock toolUse = content.asToolUse();
                        log.info("Tool: {}({})", toolUse.name(), toolUse._input());

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
                            toolError = new Exception("工具 '" + toolUse.name() + "' 没有找到");
                            log.error("Error: {}", toolError.getMessage());
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
                
                onToolExecution(toolResults);
                
                MessageParam.Content content = MessageParam.Content.ofBlockParams(toolResults);
                MessageParam toolResultMessage = MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(content)
                        .build();
                currentMessages.add(toolResultMessage);
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
                String reminder = String.format("<reminder>\n您已经 %d 个回合没有更新待办事项列表了。请更新列表以反映当前进度。\n</reminder>", roundsSinceTodo);
                toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                        .text(reminder)
                        .build()));
            }
        }

        if (compactor != null) {
            List<MessageParam> currentMessages = getCurrentMessageParams();
            compactor.microCompact(currentMessages);

            if (com.hoppinzq.agent.tool.compact.ContextCompactor.estimateTokens(currentMessages) > com.hoppinzq.agent.constant.AIConstants.TOKEN_THRESHOLD) {
                log.info("[自动压缩已触发]");
                List<MessageParam> compressed = compactor.autoCompact(currentMessages);
                currentMessages.clear();
                currentMessages.addAll(compressed);
            }

            if (manualCompactRequested) {
                log.info("[手动压缩]");
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

        if(systemPrompt != null && !systemPrompt.isEmpty()){
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
}
