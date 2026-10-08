package com.hoppinzq.agent.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.command.AgentCommandHandler;
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
 * 智能体基类（comprehensive 模块本地副本）。
 * <p>
 * 在 teams/protocols 副本基础上整合全部子系统：
 * <ol>
 *   <li>cron 定时调度（{@link #cronScheduler}，空闲/忙碌标记 + 队列注入）</li>
 *   <li>{@link #messageBus} / {@link #protocolRegistry} / {@link #protocolDispatcher} 字段</li>
 *   <li>每轮工具循环结束后 poll lead 的 inbox，按消息类型通过 ProtocolDispatcher 路由</li>
 *   <li>非空 userNotice 回灌为 user 消息，让模型感知协议状态变化</li>
 * </ol>
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
    private AgentCommandHandler commandHandler;

    public ZQAgent(LlmProvider provider, String model, List<ToolDefinition> tools) {
        this.provider = provider;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
        this.teammateProvider = provider;
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
        if (cronScheduler != null) {
            startCron();
        }
        System.out.println("开始对话吧");
        while (true) {
            // 进入空闲态：定时任务可能在这一刻被注入
            if (cronScheduler != null) {
                cronScheduler.markIdle();
            }
            // 先消费定时任务队列；若有就当作用户输入注入
            String cronPrompt = cronScheduler == null ? null : cronScheduler.consumeQueue();

            String userInput;
            if (cronPrompt != null) {
                userInput = cronPrompt;
                System.out.printf("\u001b[94m你\u001b[0m [cron]: %s%n", userInput);
            } else {
                System.out.print("\u001b[94m你\u001b[0m: ");
                userInput = scanner.nextLine();
                if (userInput.isEmpty()) {
                    continue;
                }
            }

            // 特殊命令：不发送给 LLM
            if (commandHandler != null && commandHandler.isCommand(userInput)) {
                commandHandler.handleCommand(userInput);
                continue;
            }

            appendMessage(LlmMessage.user(userInput));

            if (cronScheduler != null) {
                cronScheduler.markBusy();
            }

            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                if (cronScheduler != null) cronScheduler.markIdle();
                continue;
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                System.out.println("错误: 响应中没有消息");
                if (cronScheduler != null) cronScheduler.markIdle();
                continue;
            }
            appendMessage(message);
            recordUsageIfNeeded(response);
            printText(message);

            // canonical agentic loop：以 finish_reason == tool_calls 为循环条件
            while (hasPendingToolCalls(response)) {
                List<LlmMessage> toolResults = executeToolCalls(message);
                if (toolResults.isEmpty()) {
                    // 防御：本轮没有任何有效工具调用，继续请求只会死循环
                    break;
                }
                messageParams.addAll(toolResults);

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

            // 一轮结束：poll lead 的 mailbox，按协议消息类型路由处理
            pollInbox();

            // 一轮结束：切回 idle，让调度线程有机会把队列里的 prompt 投递出来
            if (cronScheduler != null) {
                cronScheduler.markIdle();
            }
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
     * 注意：agent-21 当前没有 SessionManager，此方法为预留接口。
     */
    private void recordUsageIfNeeded(LlmResponse response) {
        // 暂无 SessionManager，预留接口
    }

    /**
     * 向 messageParams 追加一条消息。本模块无 SessionManager，仅入内存列表；
     * 预留：若未来接入会话持久化，在此同步落盘。
     */
    protected void appendMessage(LlmMessage message) {
        messageParams.add(message);
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
}
