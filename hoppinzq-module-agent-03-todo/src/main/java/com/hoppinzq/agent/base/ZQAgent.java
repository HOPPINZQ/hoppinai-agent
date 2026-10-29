package com.hoppinzq.agent.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.session.SubAgentSessionResult;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类。
 * <p>功能扩展通过 {@link Capability} 插拔：{@code agent.capability(new XxxCapability(...))}。
 * 内核只在固定挂载点向能力分发事件（LLM 调用前后、工具执行前后、命令、提示词片段），
 * 自身不感知任何具体能力。
 *
 * @author hoppinzq
 */
@Data
public class ZQAgent {
    private String systemPrompt;
    private final Scanner scanner;
    private final String model;
    protected final LlmProvider provider;
    private final List<ToolDefinition> tools;
    protected List<LlmMessage> messageParams = new ArrayList<>();
    private String taskResult;
    private boolean taskCompleted = false;
    /**
     * 可选的命令处理器；设置后可处理 /stats、/usage、/skills、/exit 等特殊命令。
     */
    private AgentCommandHandler commandHandler;

    /**
     * 会话管理器
     */
    protected SessionManager sessionManager;

    /** 已注册的能力（顺序 = 注册顺序 = 钩子执行顺序） */
    private final List<Capability> capabilities = new ArrayList<>();

    public ZQAgent(LlmProvider provider, String model, List<ToolDefinition> tools) {
        this.provider = provider;
        this.model = model;
        this.scanner = new Scanner(System.in);
        // 拷贝为可变列表：能力注册时需要往里追加工具
        this.tools = new ArrayList<>(tools);
    }

    /**
     * 注册一个能力：合并其工具，保存钩子与命令，并回调 {@code onStart}。
     * 须在 {@link #setSessionManager} 之前调用（命令在会话初始化时收集）。
     */
    public ZQAgent capability(Capability capability) {
        capabilities.add(capability);
        for (ToolDefinition tool : capability.tools()) {
            boolean exists = tools.stream().anyMatch(t -> t.getName().equals(tool.getName()));
            if (exists) {
                System.out.printf("[能力 %s] 工具 '%s' 已存在，跳过注册%n", capability.name(), tool.getName());
                continue;
            }
            tools.add(tool);
        }
        capability.onStart(this);
        return this;
    }

    /**
     * 设置会话管理器，同时初始化命令处理器。
     */
    public void setSessionManager(SessionManager sessionManager, SkillLoader skillLoader) {
        this.sessionManager = sessionManager;
        this.commandHandler = sessionManager != null ? createCommandHandler(sessionManager, skillLoader) : null;
    }

    /**
     * 创建命令处理器：内置命令 + 全部能力注册的命令。
     */
    protected AgentCommandHandler createCommandHandler(SessionManager sessionManager, SkillLoader skillLoader) {
        List<AgentCommand> commands = new ArrayList<>();
        for (Capability capability : capabilities) {
            commands.addAll(capability.commands());
        }
        return new AgentCommandHandler(sessionManager, skillLoader, commands);
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
        System.out.println("开始对话吧（" + availableCommandsHint() + "）");
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
            appendMessage(LlmMessage.user(userInput));

            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                continue;
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                System.out.println("错误: 响应中没有消息");
                continue;
            }
            appendMessage(message);
            recordUsageIfNeeded(response);
            // 每次 create 后都打印 assistant 的文本输出，避免纯文本回复（无工具调用）被静默吞掉
            printText(message);

            // canonical agentic loop：以 finish_reason == tool_calls 为循环条件
            while (hasPendingToolCalls(response)) {
                List<LlmMessage> toolResults = executeToolCalls(message);
                if (toolResults.isEmpty()) {
                    // 防御：本轮没有任何有效工具调用，继续请求只会死循环
                    break;
                }
                messageParams.addAll(toolResults);
                if (sessionManager != null) {
                    sessionManager.onToolMessagesAppended(toolResults);
                }

                try {
                    response = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                message = response.getMessage();
                if (message == null) {
                    System.out.println("错误: 响应中没有消息");
                    break;
                }
                appendMessage(message);
                recordUsageIfNeeded(response);
                printText(message);
            }
            // 非 tool_calls 退出：检查是否被截断
            warnIfTruncated(response);
        }
    }

    /**
     * 打印 assistant 消息中的文本。空文本跳过。
     */
    private void printText(LlmMessage message) {
        String text = message.getText();
        if (text != null && !text.isBlank()) {
            System.out.printf("\u001b[93mAI\u001b[0m: %s%n", text);
        }
    }

    /**
     * 判断是否需要继续工具循环。
     * <p>以 finish_reason == tool_calls 为主判据；个别 OpenAI 兼容后端返回 tool_calls 时
     * finish_reason 可能仍是 stop，故兜底检查 toolCalls 非空。
     */
    private boolean hasPendingToolCalls(LlmResponse response) {
        if (response.getFinishReason() == LlmResponse.FinishReason.TOOL_CALLS) {
            return true;
        }
        LlmMessage message = response.getMessage();
        return message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    /** 命中 LENGTH 时打印警告，避免静默截断工具调用导致死循环。 */
    private void warnIfTruncated(LlmResponse response) {
        if (response.getFinishReason() == LlmResponse.FinishReason.LENGTH) {
            System.out.printf("\u001b[91m[警告]\u001b[0m 本轮回复被 max_tokens=%d 截断，工具调用可能不完整。建议调大 MAX_TOKENS。%n",
                    MAX_TOKENS);
        }
    }

    /**
     * 执行一轮 assistant 回复中的所有工具调用，每个调用对应一条 {@code role=tool} 消息。
     * <p>文本已由 {@link #printText(LlmMessage)} 处理，这里只负责工具；
     * 找不到工具或执行抛异常都以普通文本回灌（协议没有 isError 标记）。
     */
    private List<LlmMessage> executeToolCalls(LlmMessage message) {
        List<LlmMessage> toolResults = new ArrayList<>();
        for (LlmMessage.ToolCall call : message.getToolCalls()) {
            String callId = call.getId();
            String toolName = call.getName();
            String arguments = call.getArgumentsJson();

            // 能力拦截（如权限拒绝）：以拦截文本作为工具结果，不再执行
            String intercepted = dispatchBeforeToolCall(call);
            if (intercepted != null) {
                System.out.printf("拦截: %s%n", intercepted);
                toolResults.add(LlmMessage.tool(callId, intercepted));
                continue;
            }
            System.out.printf("\u001b[96m工具\u001b[0m: %s(%s)%n", toolName, arguments);

            String toolResult = null;
            Exception toolError = null;
            ToolDefinition matched = null;
            for (ToolDefinition tool : tools) {
                if (tool.getName().equals(toolName)) {
                    matched = tool;
                    break;
                }
            }
            if (matched == null) {
                toolError = new Exception("工具 '" + toolName + "' 没有找到");
                System.out.printf("\u001b[91m错误\u001b[0m: %s%n", toolError.getMessage());
            } else {
                try {
                    toolResult = invokeTool(matched, arguments);
                    System.out.printf("\u001b[92m结果\u001b[0m: %s%n", toolResult);
                } catch (Exception e) {
                    toolError = e;
                    System.out.printf("\u001b[91m错误\u001b[0m: %s%n", e.getMessage());
                    e.printStackTrace();
                }
            }

            String result = toolError != null ? "错误: " + toolError.getMessage() : toolResult;
            dispatchAfterToolCall(toolName, result);
            toolResults.add(LlmMessage.tool(callId, result));
        }
        return toolResults;
    }

    /**
     * 记录本次 LLM 调用的 token 使用情况（若设置了 SessionManager）。
     */
    private void recordUsageIfNeeded(LlmResponse response) {
        if (sessionManager != null && response.getUsage() != null) {
            sessionManager.recordUsage(response.getUsage());
        }
    }

    /**
     * 向 messageParams 追加一条消息；若设置了 {@link SessionManager}，
     * 同步持久化。所有需要记录历史的追加都应走此方法。
     */
    protected void appendMessage(LlmMessage message) {
        messageParams.add(message);
        if (sessionManager != null) {
            sessionManager.onMessageAppended(message);
        }
    }

    // ============================== 能力分发 ==============================

    /**
     * 启动提示中的可用命令清单（内置 + 能力注册）。
     */
    private String availableCommandsHint() {
        if (commandHandler == null) {
            return "输入 /exit 退出";
        }
        return "可用命令: " + commandHandler.availableCommands();
    }

    /** 遍历能力的 beforeToolCall；任一返回非 null 即拦截（短路） */
    private String dispatchBeforeToolCall(LlmMessage.ToolCall call) {
        for (Capability capability : capabilities) {
            for (AgentHook hook : capability.hooks()) {
                String verdict = hook.beforeToolCall(call);
                if (verdict != null) {
                    return verdict;
                }
            }
        }
        return null;
    }

    /** 遍历能力的 afterLlmResponse（拿到 finishReason / usage，可做统计或旁路记录） */
    private void dispatchAfterLlmResponse(LlmResponse response) {
        for (Capability capability : capabilities) {
            for (AgentHook hook : capability.hooks()) {
                hook.afterLlmResponse(response);
            }
        }
    }

    private void dispatchAfterToolCall(String toolName, String result) {
        for (Capability capability : capabilities) {
            for (AgentHook hook : capability.hooks()) {
                hook.afterToolCall(toolName, result);
            }
        }
    }

    /**
     * 遍历能力的 beforeLlmCall；返回新列表（非同一引用）则替换当前上下文。
     * 压缩类能力借此在每次 LLM 调用前对历史做手术。
     */
    private List<LlmMessage> dispatchBeforeLlmCall(List<LlmMessage> messages) {
        List<LlmMessage> current = messages;
        for (Capability capability : capabilities) {
            for (AgentHook hook : capability.hooks()) {
                List<LlmMessage> next = hook.beforeLlmCall(current);
                if (next != null && next != current) {
                    current = next;
                }
            }
        }
        if (current != messages) {
            messageParams.clear();
            messageParams.addAll(current);
        }
        return messageParams;
    }

    private String invokeTool(ToolDefinition tool, String arguments) throws Exception {
        JsonNode input = OBJECT_MAPPER.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        if (tool.getType() == null) {
            if (!input.isObject()) {
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数不是 JSON 对象");
            }
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.set("input", input);
            root.put("tool_name", tool.getName());
            return tool.getFunction().apply(root.toString());
        } else {
            Object pojo = OBJECT_MAPPER.treeToValue(input, tool.getType());
            return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(pojo));
        }
    }

    protected LlmResponse chatMessage(List<LlmMessage> messageParams) {
        // 能力钩子先对上下文做手术（如压缩），再发起请求
        List<LlmMessage> effective = dispatchBeforeLlmCall(messageParams);
        LlmResponse response = provider.complete(LlmRequest.builder()
                .model(model)
                .systemPrompt(systemPrompt)
                .messages(effective)
                .tools(tools)
                .maxTokens(MAX_TOKENS)
                .thinking(THINK_ENABLED)
                .thinkingBudgetTokens(THINK_BUDGET_TOKENS)
                .build());
        dispatchAfterLlmResponse(response);
        return response;
    }

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
            messageParams.add(message);

            List<LlmMessage> toolResults = new ArrayList<>();
            boolean hasToolUse = false;

            if (message.getText() != null && !message.getText().isBlank()) {
                System.out.printf("\u001b[94m（子）AI\u001b[0m: %s%n", message.getText());
            }
            for (LlmMessage.ToolCall call : message.getToolCalls()) {
                hasToolUse = true;
                System.out.printf("\u001b[96m（子）工具\u001b[0m: %s(%s)%n", call.getName(), call.getArgumentsJson());

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
                    toolError = new Exception("工具 '" + call.getName() + "' 没有找到");
                    System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", toolError.getMessage());
                }

                toolResults.add(LlmMessage.tool(call.getId(),
                        toolError != null ? "错误: " + toolError.getMessage() : toolResult));
            }

            if (!hasToolUse) {
                // 没有工具调用：文本回复即任务结果
                warnIfTruncated(response);
                return message.getText() == null ? "" : message.getText();
            }
            messageParams.addAll(toolResults);
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

        messageParams.add(LlmMessage.user(prompt));

        while (true) {
            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                return SubAgentSessionResult.error("Error: " + e.getMessage());
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                return SubAgentSessionResult.error("Error: 响应中没有消息");
            }
            messageParams.add(message);

            // 累积 token 使用情况
            if (response.getUsage() != null) {
                totalInputTokens += response.getUsage().getInputTokens() != null ? response.getUsage().getInputTokens() : 0;
                totalOutputTokens += response.getUsage().getOutputTokens() != null ? response.getUsage().getOutputTokens() : 0;
            }

            List<LlmMessage> toolResults = new ArrayList<>();
            boolean hasToolUse = false;

            if (message.getText() != null && !message.getText().isBlank()) {
                System.out.printf("\u001b[94m（子）AI\u001b[0m: %s%n", message.getText());
            }
            for (LlmMessage.ToolCall call : message.getToolCalls()) {
                hasToolUse = true;
                System.out.printf("\u001b[96m（子）工具\u001b[0m: %s(%s)%n", call.getName(), call.getArgumentsJson());

                if ("task_completed".equals(call.getName())) {
                    try {
                        JsonNode input = OBJECT_MAPPER.readTree(
                                call.getArgumentsJson() == null || call.getArgumentsJson().isBlank()
                                        ? "{}" : call.getArgumentsJson());
                        JsonNode res = input.get("result");
                        if (res != null && res.isTextual()) {
                            return SubAgentSessionResult.success(res.asText(), totalInputTokens, totalOutputTokens);
                        }
                        return SubAgentSessionResult.success("Task completed.", totalInputTokens, totalOutputTokens);
                    } catch (Exception e) {
                        return SubAgentSessionResult.error("Error parsing task_completed: " + e.getMessage());
                    }
                }

                String toolResult = null;
                Exception toolError = null;
                boolean toolFound = false;

                for (ToolDefinition tool : tools) {
                    if (tool.getName().equals(call.getName())) {
                        try {
                            toolResult = invokeTool(tool, call.getArgumentsJson());
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
                    toolError = new Exception("工具 '" + call.getName() + "' 没有找到");
                    System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", toolError.getMessage());
                }

                toolResults.add(LlmMessage.tool(call.getId(),
                        toolError != null ? "错误: " + toolError.getMessage() : toolResult));
            }

            if (!hasToolUse) {
                warnIfTruncated(response);
                return SubAgentSessionResult.success(message.getText() == null ? "" : message.getText(),
                        totalInputTokens, totalOutputTokens);
            }

            messageParams.addAll(toolResults);
        }
    }
}
