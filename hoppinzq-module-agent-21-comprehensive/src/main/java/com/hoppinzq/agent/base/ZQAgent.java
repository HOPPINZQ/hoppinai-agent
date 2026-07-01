package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    protected final AnthropicClient client;
    private final List<ToolDefinition> tools;
    protected final List<MessageParam> messageParams = new ArrayList<>();
    private CronScheduler cronScheduler;
    /** teams / protocols 共享的消息总线 */
    private MessageBus messageBus;
    /** 协议状态注册表 */
    private ProtocolRegistry protocolRegistry = new ProtocolRegistry();
    /** lead 侧 inbox 分发器 */
    private ProtocolDispatcher protocolDispatcher = new ProtocolDispatcher(protocolRegistry);
    /** 用于 spawn teammate 时复用的 client（默认与 lead 相同） */
    private AnthropicClient teammateClient;
    /** teammate 可用工具列表 */
    private List<ToolDefinition> teammateTools;
    private String taskResult;
    private boolean taskCompleted = false;
    private AgentCommandHandler commandHandler;

    public ZQAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
        this.teammateClient = client;
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

            MessageParam userMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(userInput)
                    .build();
            messageParams.add(userMessage);

            if (cronScheduler != null) {
                cronScheduler.markBusy();
            }

            Message message;
            try {
                message = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                if (cronScheduler != null) cronScheduler.markIdle();
                continue;
            }
            messageParams.add(message.toParam());
            recordUsageIfNeeded(message);
            printText(message);

            while (isToolUse(message)) {
                MessageParam toolResultMessage = executeToolCalls(message);
                messageParams.add(toolResultMessage);
                try {
                    message = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                messageParams.add(message.toParam());
                recordUsageIfNeeded(message);
                printText(message);
            }
            warnIfTruncated(message);

            // 一轮结束：poll lead 的 mailbox，按协议消息类型路由处理
            pollInbox();

            // 一轮结束：切回 idle，让调度线程有机会把队列里的 prompt 投递出来
            if (cronScheduler != null) {
                cronScheduler.markIdle();
            }
        }
    }

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

    private boolean isToolUse(Message message) {
        return message.stopReason()
                .map(StopReason.TOOL_USE::equals)
                .orElse(false);
    }

    private void warnIfTruncated(Message message) {
        boolean maxTokens = message.stopReason()
                .map(StopReason.MAX_TOKENS::equals)
                .orElse(false);
        if (maxTokens) {
            System.out.printf("\u001b[91m[警告]\u001b[0m 本轮回复被 max_tokens=%d 截断，工具调用可能不完整。建议调大 MAX_TOKENS。%n",
                    MAX_TOKENS);
        }
    }

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
     * 注意：agent-21 当前没有 SessionManager，此方法为预留接口。
     */
    private void recordUsageIfNeeded(Message message) {
        // 暂无 SessionManager，预留接口
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
                messageParams.add(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content("[protocol] " + outcome.getUserNotice())
                        .build());
            }
        }
    }

    private String invokeTool(ToolDefinition tool, JsonValue input) {
        if (tool.getType() == null) {
            if (input.asObject().isEmpty()) {
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数不是 JSON 对象");
            }
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.set("input", input.convert(JsonNode.class));
            root.put("tool_name", tool.getName());
            return tool.getFunction().apply(root.toString());
        } else {
            return tool.getFunction().apply(Objects.requireNonNull(input.convert(tool.getType())).toString());
        }
    }

    protected Message chatMessage(List<MessageParam> messageParams){
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

        if(systemPrompt != null && !systemPrompt.isEmpty()){
            messageBuilder.system(systemPrompt);
        }

        messageBuilder.maxTokens(MAX_TOKENS);
        messageBuilder.temperature(TEMPERATURE);

        MessageCreateParams params = messageBuilder.build();
        return client.messages().create(params);
    }
}
