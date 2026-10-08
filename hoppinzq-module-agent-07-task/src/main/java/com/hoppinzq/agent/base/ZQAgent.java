package com.hoppinzq.agent.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类。
 * <p>只面向协议中立的 {@link LlmProvider}/{@link LlmMessage} 编程，
 * 协议差异（Anthropic / OpenAI）由 Provider 实现类封装。
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
    protected final List<LlmMessage> messageParams = new ArrayList<>();
    private String taskResult;
    private boolean taskCompleted = false;
    /** 可选的会话管理器；设置后，每条消息会自动持久化，启动时自动恢复历史。 */
    private SessionManager sessionManager;
    /**
     * 可选的命令处理器；设置后可处理 /stats、/usage、/exit 等特殊命令。
     */
    private AgentCommandHandler commandHandler;

    public ZQAgent(LlmProvider provider, String model, List<ToolDefinition> tools) {
        this.provider = provider;
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
            // 见 https://platform.openai.com/docs/guides/function-calling
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
     * <p>DeepSeek 等后端返回 tool_calls 时文本常为 null。
     */
    private void printText(LlmMessage message) {
        String text = message.getText();
        if (text != null && !text.isBlank()) {
            System.out.printf("\u001b[93mAI\u001b[0m: %s%n", text);
        }
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

    /**
     * 命中 LENGTH 时打印警告，避免静默截断工具调用导致死循环。
     */
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
     * <p>协议要求每个 tool_call_id 必须有且仅有一条应答消息（含失败），否则下次请求 400。
     */
    private List<LlmMessage> executeToolCalls(LlmMessage message) {
        List<LlmMessage> toolMessages = new ArrayList<>();
        for (LlmMessage.ToolCall call : message.getToolCalls()) {
            String callId = call.getId();
            String toolName = call.getName();
            String arguments = call.getArgumentsJson();
            System.out.printf("\u001b[96m工具\u001b[0m: %s(%s)%n", toolName, arguments);

            String result;
            try {
                ToolDefinition matched = null;
                for (ToolDefinition tool : tools) {
                    if (tool.getName().equals(toolName)) {
                        matched = tool;
                        break;
                    }
                }
                if (matched == null) {
                    throw new IllegalArgumentException("工具 '" + toolName + "' 没有找到");
                }
                result = invokeTool(matched, arguments);
            } catch (Exception e) {
                result = "错误: " + e.getMessage();
                System.out.printf("\u001b[91m错误\u001b[0m: %s%n", e.getMessage());
                e.printStackTrace();
            }
            System.out.printf("\u001b[92m结果\u001b[0m: %s%n", result);

            toolMessages.add(LlmMessage.tool(callId, result));
        }
        return toolMessages;
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
     * <p>注意：一轮的多条 {@code role=tool} 结果不走此方法，
     * 由 {@link SessionManager#onToolMessagesAppended(List)} 合并落盘。
     */
    protected void appendMessage(LlmMessage message) {
        messageParams.add(message);
        if (sessionManager != null) {
            sessionManager.onMessageAppended(message);
        }
    }

    /**
     * 工具执行后的回调钩子，子类可重写以注入额外内容（如提醒、压缩等）。
     * 注意：基类的 {@link #executeToolCalls(LlmMessage)} 不再调用此方法；
     * 仅为兼容已有子类重写而保留。
     */
    protected void onToolExecution(List<LlmMessage> toolResults) {
        // 默认空实现
    }

    private String invokeTool(ToolDefinition tool, String arguments) throws Exception {
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
        return provider.complete(LlmRequest.builder()
                .model(model)
                .systemPrompt(systemPrompt)
                .messages(messageParams)
                .tools(tools)
                .maxTokens(MAX_TOKENS)
                .build());
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

            if (message.getText() != null && !message.getText().isBlank()) {
                System.out.printf("\u001b[94m（子）AI\u001b[0m: %s%n", message.getText());
            }

            // 没有工具调用：把文本作为任务结果返回
            if (!hasPendingToolCalls(response)) {
                return message.getText() == null ? "" : message.getText();
            }

            for (LlmMessage.ToolCall call : message.getToolCalls()) {
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

                // 协议没有 isError 标记：错误以 "错误: " 文本前缀回灌
                messageParams.add(LlmMessage.tool(call.getId(),
                        toolError != null ? "错误: " + toolError.getMessage() : toolResult));
            }
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
                if (response.getUsage().getInputTokens() != null) {
                    totalInputTokens += response.getUsage().getInputTokens();
                }
                if (response.getUsage().getOutputTokens() != null) {
                    totalOutputTokens += response.getUsage().getOutputTokens();
                }
            }

            if (message.getText() != null && !message.getText().isBlank()) {
                System.out.printf("\u001b[94m（子）AI\u001b[0m: %s%n", message.getText());
            }

            // 没有工具调用：把文本作为任务结果返回
            if (!hasPendingToolCalls(response)) {
                warnIfTruncated(response);
                return SubAgentSessionResult.success(message.getText() == null ? "" : message.getText(),
                        totalInputTokens, totalOutputTokens);
            }

            // 处理本轮全部工具调用；其中 task_completed 直接短路返回
            for (LlmMessage.ToolCall call : message.getToolCalls()) {
                if ("task_completed".equals(call.getName())) {
                    try {
                        JsonNode input = OBJECT_MAPPER.readTree(
                                call.getArgumentsJson() == null || call.getArgumentsJson().isBlank()
                                        ? "{}" : call.getArgumentsJson());
                        JsonNode res = input.get("result");
                        if (res != null && res.isTextual()) {
                            return SubAgentSessionResult.success(res.asText(),
                                    totalInputTokens, totalOutputTokens);
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
                    if (tool.getName().equals(call.getName())) {
                        matched = tool;
                        break;
                    }
                }
                if (matched == null) {
                    toolError = new Exception("工具 '" + call.getName() + "' 没有找到");
                    System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", toolError.getMessage());
                } else {
                    try {
                        toolResult = invokeTool(matched, call.getArgumentsJson());
                        System.out.printf("\u001b[92m（子）结果\u001b[0m: %s%n", toolResult);
                    } catch (Exception e) {
                        toolError = e;
                        System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", e.getMessage());
                        e.printStackTrace();
                    }
                }

                // 协议没有 isError 标记：错误以 "错误: " 文本前缀回灌
                messageParams.add(LlmMessage.tool(call.getId(),
                        toolError != null ? "错误: " + toolError.getMessage() : toolResult));
            }
        }
    }
}
