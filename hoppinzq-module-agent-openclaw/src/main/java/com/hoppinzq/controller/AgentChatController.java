package com.hoppinzq.controller;

import com.alibaba.fastjson.JSON;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.context.SessionContextHolder;
import com.hoppinzq.agent.base.WebZQAgent;
import com.hoppinzq.agent.service.ChatMessageService;
import com.hoppinzq.agent.service.ChatSessionService;
import com.hoppinzq.agent.tool.background.BackgroundManager;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import com.hoppinzq.agent.tool.task.TaskManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.Tools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;
import static com.hoppinzq.agent.tool.ToolDefinition.ContentSearchDefinition;

@Slf4j
@RestController
@RequestMapping("/agent")
@CrossOrigin(origins = "*")
public class AgentChatController {

    private final AnthropicClient client;
    private final List<ToolDefinition> tools;
    private final WebZQAgent zqAgent;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final SkillLoader skillLoader = new SkillLoader();

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    public AgentChatController() {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();
        
        BackgroundManager backgroundManager = new BackgroundManager();
        TaskManager taskManager = new TaskManager();
        ContextCompactor compactor = new ContextCompactor(client, MODEL);
        TodoManager todoManager = new TodoManager();


        this.zqAgent = new WebZQAgent(client, MODEL);
        this.zqAgent.initManagers(backgroundManager, taskManager, compactor, skillLoader, todoManager);

        // 添加基础工具
        List<ToolDefinition> addTools = new ArrayList<>();
        addTools.add(BashDefinition);
        addTools.add(EditFileDefinition);
        addTools.add(WriteFileDefinition);
        addTools.add(ReadFileDefinition);
        addTools.add(ListFilesDefinition);
        addTools.add(ContentSearchDefinition);

        // 补充demo中的额外工具
        addTools.add(SubAgentDefinition);
        addTools.add(TodoDefinition);
        addTools.add(SkillsDefinition);
        addTools.add(ContentCompactDefinition);
        addTools.add(TaskCreateDefinition);
        addTools.add(TaskUpdateDefinition);
        addTools.add(TaskListDefinition);
        addTools.add(TaskGetDefinition);
        addTools.add(BackgroundRunDefinition);
        addTools.add(CheckBackgroundDefinition);

        this.tools = addTools;
        this.zqAgent.setTools(addTools);
        this.zqAgent.setSystemPrompt(buildSystemPrompt());
    }

    private String buildSystemPrompt() {
        if (REACT_ENABLE) {
            StringBuilder toolDescriptions = new StringBuilder();
            for (ToolDefinition tool : tools) {
                toolDescriptions.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
                try {
                    toolDescriptions.append("  参数 Schema: ").append(OBJECT_MAPPER.writeValueAsString(tool.getInputSchema())).append("\n");
                } catch (Exception ignored) {}
            }

            String actionNames = tools.stream().map(ToolDefinition::getName).reduce((a, b) -> a + "," + b).orElse("");

            return String.format("""
                你是一个专业的编程智能体，具有强大的后台任务执行能力。你使用 ReAct 模式结合推理和行动来解决复杂问题。
                
                ## ReAct 模式说明
                
                当需要使用工具时，请按照以下格式思考和行动：
                
                Thought: 思考并确定下一步的最佳行动方案
                Action: %s
                Action Input: 工具参数，必须是 JSON 对象
                Observation: 工具执行结果，你不能私自赋值，刚开始没值
                
                ... (Thought/Action/Action Input/Observation 可以重复N次)
                
                ## 重要规则
                
                1. 不使用工具时，回复中不要出现 Thought、Action、Action Input；
                2. 使用工具前，先检查是否缺少必要参数，缺少必要参数时直接向用户提问，不要出现 Thought、Action、Action Input；
                3. 对话时，一次只能返回一个 Thought/Action/Action Input/Observation，绝不能返回多个；
                4. 绝对不能私自给 Observation 赋值，Observation 是 Action 的返回值；
                5. 工具执行遇到问题时，向用户寻求帮助；
                6. 需要执行同一个工具多次时，Action Input 可以出现多次。
                
                ## 示例
                
                用户: 请列出当前目录的文件
                
                AI:
                Thought: 用户想要查看当前目录的文件列表，我应该使用 list_files 工具
                Action: list_files
                Action Input: {}
                
                用户: (工具执行结果) Observation: ["file1.txt", "file2.java", ...]
                
                AI: 我找到了以下文件：file1.txt, file2.java, ...
                
                ## 可用工具
                
                %s
                
                ## 可用技能
                
                %s
                
                ### 技能使用策略
                
                1. **主动学习**：遇到不熟悉的领域时，主动使用 load_skill 工具获取专业知识
                2. **何时加载技能**：遇到不熟悉的编程概念、需要遵循特定最佳实践、执行标准化工作流程时
                3. **技能加载流程**：识别知识缺口 → 使用 load_skill 加载 → 阅读理解 → 应用知识解决问题
                
                ## 工作环境
                - 工作目录: %s
                - 操作系统: %s
                
                请记住：你的目标是帮助用户高效地完成任务。使用 ReAct 模式，你可以通过思考、行动、观察的循环来逐步解决问题。
                ""","工具名称，必须是[" + actionNames + "]中的一个", toolDescriptions, skillLoader.getDescriptions(), ROOT, System.getProperty("os.name"));
        } else {
            return """
                # 角色
                你是一个专业的编程智能体，具有强大的后台任务执行能力。
                
                ## 工作环境
                - 工作目录: %s
                - 操作系统: %s
                
                # 核心能力
                
                ## 文件操作
                - read_file: 读取文件内容
                - write_file: 写入文件内容
                - edit_file: 编辑文件（替换字符串）
                - list_files: 列出目录下的文件
                
                ## 代码搜索
                - content_search: 使用ripgrep搜索代码内容，支持正则表达式
                
                ## 命令执行
                - bash: 执行Shell命令（适用于快速命令）
                - background_run: 在后台执行耗时命令（不阻塞对话）
                - check_background: 检查后台任务状态
                
                ## 任务管理
                - task_create: 创建新任务
                - task_update: 更新任务状态和依赖关系
                - task_list: 列出所有任务
                - task_get: 获取任务详情
                - todo: 管理待办事项列表
                
                ## 高级功能
                - sub_agent: 委托子智能体处理复杂任务
                - load_skill: 加载特定技能
                - compact: 手动触发对话压缩
                
                # 工作指南
                
                ## 后台任务使用
                1. **识别耗时命令**: 对于预计耗时超过5秒的命令，使用background_run
                   - 常见场景: npm install, pytest, docker build, git clone, mvn build
                   - 批量测试: pytest, npm test
                   - 数据处理: 大文件转换、批量操作
                
                2. **后台任务流程**:
                   ```
                   用户请求 → background_run(command)
                            → 立即返回task_id
                            → 任务在后台执行
                            → 完成后自动注入结果到对话
                   ```
                
                3. **状态监控**:
                   - 使用check_background查看所有后台任务
                   - 后台任务完成时会自动通知
                   - 无需手动轮询，系统会自动注入结果
                
                ## 对话管理
                1. **自动压缩**: 当对话历史过长时，系统会自动压缩并保留关键信息
                2. **手动压缩**: 使用compact工具可主动触发压缩
                3. **待办提醒**: 系统会定期提醒更新待办事项
                
                ## 最佳实践
                1. **优先使用后台任务**: 避免长时间阻塞对话
                2. **合理使用任务管理**: 将大任务分解为小任务，便于跟踪进度
                3. **及时更新待办**: 保持待办事项列表的准确性
                4. **充分利用搜索**: 使用content_search快速定位代码
                
                # 重要提醒
                - 后台任务会自动通知结果，无需手动检查
                - 对话过长时会自动压缩，不用担心token限制
                - 定期更新待办事项，保持任务追踪的准确性
                - 对于复杂任务，使用sub_agent委托处理
                - 遇到问题时，使用content_search查找相关代码
                
                # 技能系统
                
                ## 可用技能
                
                %s
                
                ## 技能使用策略
                
                1. **主动学习**：遇到不熟悉的领域时，主动使用 load_skill 工具获取专业知识
                2. **何时加载技能**：遇到不熟悉的编程概念、需要遵循特定最佳实践、执行标准化工作流程时
                3. **技能加载流程**：识别知识缺口 → 使用 load_skill 加载 → 阅读理解 → 应用知识解决问题
                """.formatted(ROOT, System.getProperty("os.name"), skillLoader.getDescriptions());
        }
    }

    @PostMapping(value = "/runTask")
    public String runTask(@RequestBody Map<String, String> requestBody,
                          @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        String message = requestBody.get("message");
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("message不能为空");
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            SessionContextHolder.set(sessionId);
        }
        try {
            return zqAgent.runTask(message);
        } catch (Exception e) {
            log.error("Agent runTask error", e);
            return "Error: " + e.getMessage();
        } finally {
            SessionContextHolder.clear();
        }
    }

    @PostMapping(value = "/streamChat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody Map<String, String> requestBody,
                                   @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        String message = requestBody.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("message不能为空"));
        }

        final String finalSessionId = (sessionId != null && !sessionId.isEmpty()) ? sessionId : "default";

        List<MessageParam> messages = zqAgent.getMessageParams(finalSessionId);
        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(message)
                .build());

        chatSessionService.createSession(finalSessionId, "新会话");
        int userOrder = chatMessageService.getNextMessageOrder(finalSessionId);
        chatMessageService.saveMessage(finalSessionId, "user", message, userOrder);

        final StringBuilder assistantResponse = new StringBuilder();
        final AtomicLong totalInputTokens = new AtomicLong(0);
        final AtomicLong totalOutputTokens = new AtomicLong(0);

        return Flux.create(emitter -> {
            SessionContextHolder.set(finalSessionId);
            executorService.submit(() -> {
                try {
                    processAgentStream(messages, emitter, finalSessionId, assistantResponse, totalInputTokens, totalOutputTokens);
                } catch (Exception e) {
                    log.error("Agent stream error", e);
                    emitter.error(e);
                } finally {
                    SessionContextHolder.clear();
                }
            });
        });
    }

    private void appendToolCallToResponse(StringBuilder assistantResponse, String toolId, String toolName, String status, Object toolInput, Object toolResult) {
        Map<String, Object> toolCall = new java.util.HashMap<>();
        toolCall.put("type", "tool_call");
        toolCall.put("tool_id", toolId);
        toolCall.put("tool_name", toolName);
        toolCall.put("status", status);
        if (toolInput != null) {
            toolCall.put("tool_input", toolInput);
        }
        if (toolResult != null) {
            toolCall.put("tool_result", toolResult);
        }
        assistantResponse.append("\n<!--TOOL:").append(JSON.toJSONString(toolCall)).append("-->\n");
    }

    private void persistAssistantResponse(String sessionId, StringBuilder assistantResponse, long token) {
        if (assistantResponse.length() > 0) {
            try {
                int order = chatMessageService.getNextMessageOrder(sessionId);
                chatMessageService.saveMessage(sessionId, "assistant", assistantResponse.toString(), order, token);
                chatSessionService.updateSessionTime(sessionId);
                assistantResponse.setLength(0);
            } catch (Exception e) {
                log.error("Failed to persist assistant response", e);
            }
        }
    }

    private void processAgentStream(List<MessageParam> messages, FluxSink<String> emitter,
                                    String sessionId, StringBuilder assistantResponse,
                                    AtomicLong totalInputTokens, AtomicLong totalOutputTokens) {
        try (StreamResponse<RawMessageStreamEvent> streamResponse = zqAgent.chatMessageStream(messages)) {
            Stream<RawMessageStreamEvent> stream = streamResponse.stream();

            StringBuilder fullTextResponse = new StringBuilder();
            StringBuilder toolUseId = new StringBuilder();
            StringBuilder toolName = new StringBuilder();
            StringBuilder toolInputStr = new StringBuilder();
            AtomicBoolean isToolUse = new AtomicBoolean(false);

            stream.forEach(event -> {
                if (emitter.isCancelled()) {
                    throw new RuntimeException("Client disconnected");
                }
                
                if (event.isMessageStart()) {
                    try {
                        RawMessageStartEvent startEvent = event.asMessageStart();
                        com.anthropic.models.messages.Usage usage = startEvent.message().usage();
                        if (usage != null && usage.inputTokens() != 0) {
                            totalInputTokens.addAndGet(usage.inputTokens());
                        }
                    } catch (Exception e) {
                        log.debug("Failed to extract input tokens from message_start", e);
                    }
                } else if (event.isMessageDelta()) {
                    try {
                        RawMessageDeltaEvent deltaEvent = event.asMessageDelta();
                        MessageDeltaUsage usage = deltaEvent.usage();
                        if (usage != null && usage.outputTokens() != 0) {
                            totalOutputTokens.addAndGet(usage.outputTokens());
                        }
                    } catch (Exception e) {
                        log.debug("Failed to extract output tokens from message_delta", e);
                    }
                } else if (event.isContentBlockStart()) {
                    RawContentBlockStartEvent blockStart = event.asContentBlockStart();
                    if (blockStart.contentBlock().isToolUse()) {
                        isToolUse.set(true);
                        ToolUseBlock toolUse = blockStart.contentBlock().asToolUse();
                        toolUseId.append(toolUse.id());
                        toolName.append(toolUse.name());
                    }
                } else if (event.isContentBlockDelta()) {
                    RawContentBlockDeltaEvent deltaEvent = event.asContentBlockDelta();
                    if (deltaEvent.delta().isText()) {
                        String text = deltaEvent.delta().asText().text();
                        fullTextResponse.append(text);
                        assistantResponse.append(text);
                        // Emit text delta
                        emitter.next(JSON.toJSONString(Map.of(
                            "type", "content_block_delta",
                            "index", deltaEvent.index(),
                            "delta", Map.of(
                                "type", "text_delta",
                                "text", text
                            )
                        )));
                    } else if (deltaEvent.delta().isInputJson()) {
                        String partialJson = deltaEvent.delta().asInputJson().partialJson();
                        toolInputStr.append(partialJson);
                    }
                } else if (event.isContentBlockStop()) {
                    if (isToolUse.get()) {
                        // Tool use complete, let's invoke the tool
                        try {
                            String toolResult = null;
                            boolean found = false;
                            
                            Object parsedInput;
                            try {
                                parsedInput = JSON.parse(toolInputStr.toString());
                            } catch (Exception e) {
                                parsedInput = toolInputStr.toString();
                            }
                            
                            emitter.next(JSON.toJSONString(Map.of(
                                "type", "tool_status",
                                "status", "calling",
                                "tool_name", toolName.toString(),
                                "tool_id", toolUseId.toString(),
                                "tool_input", parsedInput
                            )));
                            appendToolCallToResponse(assistantResponse, toolUseId.toString(), toolName.toString(), "calling", parsedInput, null);
                            
                            if ("task_completed".equals(toolName.toString())) {
                                Object parsedInputTemp;
                                try {
                                    parsedInputTemp = JSON.parse(toolInputStr.toString());
                                } catch (Exception e) {
                                    parsedInputTemp = toolInputStr.toString();
                                }
                                
                                emitter.next(JSON.toJSONString(Map.of(
                                    "type", "task_completed",
                                    "result", parsedInputTemp
                                )));
                                long totalTokens = totalInputTokens.get() + totalOutputTokens.get();
                                persistAssistantResponse(sessionId, assistantResponse, totalTokens);
                                emitter.complete();
                                return;
                            }
                            
                            for (ToolDefinition tool : tools) {
                                if (tool.getName().equals(toolName.toString())) {
                                    JsonValue jsonValue = JsonValue.from(JSON.parseObject(toolInputStr.toString()));
                                    toolResult = zqAgent.invokeTool(tool, jsonValue);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                toolResult = "Error: Tool not found";
                                Map<String, Object> errMap = new java.util.HashMap<>();
                                errMap.put("type", "tool_status");
                                errMap.put("status", "unknown");
                                errMap.put("tool_name", toolName.toString());
                                errMap.put("tool_id", toolUseId.toString());
                                emitter.next(JSON.toJSONString(errMap));
                                appendToolCallToResponse(assistantResponse, toolUseId.toString(), toolName.toString(), "unknown", null, toolResult);
                            } else {
                                Map<String, Object> succMap = new java.util.HashMap<>();
                                succMap.put("type", "tool_status");
                                succMap.put("status", "success");
                                succMap.put("tool_name", toolName.toString());
                                succMap.put("tool_id", toolUseId.toString());
                                succMap.put("tool_result", toolResult);
                                emitter.next(JSON.toJSONString(succMap));
                                appendToolCallToResponse(assistantResponse, toolUseId.toString(), toolName.toString(), "success", null, toolResult);
                            }

                            // Add assistant message and tool result to messages list
                            List<ContentBlockParam> assistantBlocks = new ArrayList<>();
                            if (fullTextResponse.length() > 0) {
                                assistantBlocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(fullTextResponse.toString()).build()));
                            }
                            assistantBlocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                                    .id(toolUseId.toString())
                                    .name(toolName.toString())
                                    .input(JsonValue.from(JSON.parseObject(toolInputStr.toString())))
                                    .build()));

                            messages.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT).content(MessageParam.Content.ofBlockParams(assistantBlocks)).build());

                            List<ContentBlockParam> toolResults = new ArrayList<>();
                            toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                                            .toolUseId(toolUseId.toString())
                                            .content(toolResult)
                                            .build()));

                            zqAgent.onToolExecution(toolResults);

                            messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(MessageParam.Content.ofBlockParams(toolResults)).build());

                            // Recursively call for next step
                            if (!emitter.isCancelled()) {
                                processAgentStream(messages, emitter, sessionId, assistantResponse, totalInputTokens, totalOutputTokens);
                            }

                        } catch (Exception e) {
                            log.error("Tool execution failed", e);
                            Map<String, Object> errMap = new java.util.HashMap<>();
                            errMap.put("type", "tool_status");
                            errMap.put("status", "error");
                            errMap.put("tool_name", toolName.toString());
                            errMap.put("tool_id", toolUseId.toString());
                            errMap.put("error_msg", e.getMessage() != null ? e.getMessage() : "Unknown error");
                            errMap.put("tool_result", e.getMessage() != null ? e.getMessage() : "Unknown error");
                            emitter.next(JSON.toJSONString(errMap));
                            appendToolCallToResponse(assistantResponse, toolUseId.toString(), toolName.toString(), "error", null, e.getMessage() != null ? e.getMessage() : "Unknown error");
                            emitter.error(e);
                        }
                    }
                } else if (event.isMessageStop()) {
                    if (REACT_ENABLE) {
                        String resultText = fullTextResponse.toString();
                        if (resultText.contains("Action:")) {
                            try {
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
                                    String fakeToolId = "react_" + System.currentTimeMillis();
                                    
                                    Object parsedInput;
                                    try {
                                        parsedInput = JSON.parse(actionInputStr);
                                    } catch (Exception e) {
                                        parsedInput = actionInputStr;
                                    }
                                    
                                    Map<String, Object> callingMap = new java.util.HashMap<>();
                                    callingMap.put("type", "tool_status");
                                    callingMap.put("status", "calling");
                                    callingMap.put("tool_name", action);
                                    callingMap.put("tool_id", fakeToolId);
                                    callingMap.put("tool_input", parsedInput);
                                    emitter.next(JSON.toJSONString(callingMap));
                                    appendToolCallToResponse(assistantResponse, fakeToolId, action, "calling", parsedInput, null);
                                    
                                    if ("task_completed".equals(action)) {
                                        Map<String, Object> completedMap = new java.util.HashMap<>();
                                        completedMap.put("type", "task_completed");
                                        completedMap.put("result", parsedInput);
                                        emitter.next(JSON.toJSONString(completedMap));
                                        long totalTokens = totalInputTokens.get() + totalOutputTokens.get();
                                        persistAssistantResponse(sessionId, assistantResponse, totalTokens);
                                        emitter.complete();
                                        return;
                                    }
                                    
                                    String toolResult = null;
                                    boolean found = false;
                                    for (ToolDefinition tool : tools) {
                                        if (tool.getName().equals(action)) {
                                            JsonValue jsonValue = JsonValue.from(JSON.parseObject(actionInputStr));
                                            toolResult = zqAgent.invokeTool(tool, jsonValue);
                                            found = true;
                                            break;
                                        }
                                    }
                                    
                                    Map<String, Object> resultMap = new java.util.HashMap<>();
                                    resultMap.put("type", "tool_status");
                                    resultMap.put("tool_name", action);
                                    resultMap.put("tool_id", fakeToolId);
                                    if (!found) {
                                        toolResult = "Error: Tool not found";
                                        resultMap.put("status", "unknown");
                                    } else {
                                        resultMap.put("status", "success");
                                        resultMap.put("tool_result", toolResult);
                                    }
                                    emitter.next(JSON.toJSONString(resultMap));
                                    appendToolCallToResponse(assistantResponse, fakeToolId, action, found ? "success" : "unknown", null, toolResult);
                                    
                                    messages.add(MessageParam.builder()
                                            .role(MessageParam.Role.ASSISTANT)
                                            .content(resultText)
                                            .build());
                                            
                                    messages.add(MessageParam.builder()
                                            .role(MessageParam.Role.USER)
                                            .content("Observation: " + toolResult)
                                            .build());
                                            
                                    if (!emitter.isCancelled()) {
                                        processAgentStream(messages, emitter, sessionId, assistantResponse, totalInputTokens, totalOutputTokens);
                                    }
                                } else {
                                    messages.add(MessageParam.builder()
                                            .role(MessageParam.Role.ASSISTANT)
                                            .content(resultText)
                                            .build());
                                    long totalTokens = totalInputTokens.get() + totalOutputTokens.get();
                                    emitter.next(JSON.toJSONString(Map.of("type", "end", "token", totalTokens)));
                                    persistAssistantResponse(sessionId, assistantResponse, totalTokens);
                                    emitter.complete();
                                }
                            } catch (Exception e) {
                                log.error("ReAct execution failed", e);
                                emitter.error(e);
                            }
                        } else {
                            messages.add(MessageParam.builder()
                                    .role(MessageParam.Role.ASSISTANT)
                                    .content(resultText)
                                    .build());
                            long totalTokens = totalInputTokens.get() + totalOutputTokens.get();
                            emitter.next(JSON.toJSONString(Map.of("type", "end", "token", totalTokens)));
                            persistAssistantResponse(sessionId, assistantResponse, totalTokens);
                            emitter.complete();
                        }
                    } else if (!isToolUse.get()) {
                        long totalTokens = totalInputTokens.get() + totalOutputTokens.get();
                        emitter.next(JSON.toJSONString(Map.of("type", "end", "token", totalTokens)));
                        persistAssistantResponse(sessionId, assistantResponse, totalTokens);
                        emitter.complete();
                    }
                }
            });
        } catch (Exception e) {
            log.error("Stream closed with error", e);
            emitter.error(e);
        }
    }
}
