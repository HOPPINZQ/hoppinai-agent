package com.hoppinzq.agent.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.bus.MailboxMessage;
import com.hoppinzq.agent.tool.bus.MessageBus;
import com.hoppinzq.agent.tool.cron.CronScheduler;
import com.hoppinzq.agent.tool.protocol.DispatchOutcome;
import com.hoppinzq.agent.tool.protocol.ProtocolDispatcher;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类（protocols 模块本地副本）。
 * <p>
 * 在 cron 副本基础上新增：
 * <ol>
 *   <li>{@link #messageBus} / {@link #protocolRegistry} / {@link #protocolDispatcher} 字段</li>
 *   <li>每轮工具循环结束后 poll lead 的 inbox，按消息类型通过 ProtocolDispatcher 路由</li>
 *   <li>非空 userNotice 回灌为 user 消息，让模型感知协议状态变化</li>
 * </ol>
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
    private CronScheduler cronScheduler;
    /** teams / protocols 共享的消息总线 */
    private MessageBus messageBus;
    /** 协议状态注册表 */
    private ProtocolRegistry protocolRegistry = new ProtocolRegistry();
    /** lead 侧 inbox 分发器 */
    private ProtocolDispatcher protocolDispatcher = new ProtocolDispatcher(protocolRegistry);
    /** 用于 spawn teammate 时复用的 provider（默认与 lead 相同） */
    private LlmProvider teammateProvider;
    /** teammate 可用工具列表 */
    private List<ToolDefinition> teammateTools;
    private String taskResult;
    private boolean taskCompleted = false;
    /** 可选的会话管理器；设置后，每条消息会自动持久化，启动时自动恢复历史。 */
    private SessionManager sessionManager;
    private AgentCommandHandler commandHandler;

    public ZQAgent(LlmProvider provider, String model, List<ToolDefinition> tools) {
        this.provider = provider;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
        this.teammateProvider = provider;
    }

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.commandHandler = sessionManager != null ? new AgentCommandHandler(sessionManager) : null;
    }

    public void setCommandHandler(AgentCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    public void startCron() {
        if (cronScheduler == null) {
            throw new IllegalStateException("cronScheduler 未设置");
        }
        cronScheduler.restoreDurable();
        cronScheduler.start();
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
        if (cronScheduler != null) {
            startCron();
        }
        System.out.println("开始对话吧");
        while (true) {
            System.out.print("\u001b[94m你\u001b[0m: ");
            String userInput = scanner.nextLine();
            if (userInput.isEmpty()) {
                continue;
            }

            // 命令处理（在创建 userMessage 之前）
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
     * 打印 assistant 消息中的所有文本块。空文本跳过。
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

    /** 命中 LENGTH 时打印警告，避免静默截断工具调用导致死循环。 */
    private void warnIfTruncated(LlmResponse response) {
        if (response.getFinishReason() == LlmResponse.FinishReason.LENGTH) {
            System.out.printf("\u001b[91m[警告]\u001b[0m 本轮回复被 max_tokens=%d 截断，工具调用可能不完整。建议调大 MAX_TOKENS。%n",
                    MAX_TOKENS);
        }
    }

    /**
     * 执行一轮 assistant 回复中的所有工具调用，返回工具结果消息列表。
     * <p>文本块已由 {@link #printText(LlmMessage)} 处理，这里只负责工具；
     * 找不到工具或执行抛异常都会以「错误: 」前缀回灌给模型。
     */
    private List<LlmMessage> executeToolCalls(LlmMessage message) {
        List<LlmMessage> toolResults = new ArrayList<>();
        for (LlmMessage.ToolCall call : message.getToolCalls()) {
            String callId = call.getId();
            String toolName = call.getName();
            String arguments = call.getArgumentsJson();
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

            toolResults.add(LlmMessage.tool(callId,
                    toolError != null ? "错误: " + toolError.getMessage() : toolResult));
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

    /**
     * 读取并清空 lead 的 mailbox，把每条消息交给 {@link ProtocolDispatcher} 处理；
     * 处理结果中非空的 userNotice 会作为 user 消息回灌给 LLM，让模型感知协议状态变化。
     */
    private void pollInbox() {
        if (messageBus == null) {
            return;
        }
        List<MailboxMessage> msgs = messageBus.readInbox("lead");
        if (msgs.isEmpty()) {
            return;
        }
        for (MailboxMessage msg : msgs) {
            DispatchOutcome outcome = protocolDispatcher.handle(msg);
            if (outcome.getUserNotice() != null && !outcome.getUserNotice().isBlank()) {
                appendMessage(LlmMessage.user("[protocol] " + outcome.getUserNotice()));
            }
        }
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
        String effectiveSystemPrompt = systemPrompt;
        // 添加命令提示
        if (sessionManager != null) {
            String cmdHint = "\n\n特殊命令（不发送给模型）：/stats - 查看会话统计，/usage - 查看token使用明细，/exit - 退出程序";
            effectiveSystemPrompt = systemPrompt == null ? cmdHint : systemPrompt + cmdHint;
        }
        return provider.complete(LlmRequest.builder()
                .model(model)
                .systemPrompt(effectiveSystemPrompt)
                .messages(messageParams)
                .tools(tools)
                .maxTokens(MAX_TOKENS)
                .build());
    }
}
