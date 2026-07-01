package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    /** 可选的会话管理器；设置后，每条消息会自动持久化，启动时自动恢复历史。 */
    private SessionManager sessionManager;
    private AgentCommandHandler commandHandler;

    public ZQAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
        this.teammateClient = client;
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
            MessageParam userMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(userInput)
                    .build();
            appendMessage(userMessage);

            Message message;
            try {
                message = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                continue;
            }
            appendMessage(message.toParam());
            recordUsageIfNeeded(message);
            // 每次 create 后都打印 assistant 的文本输出，避免纯文本回复（无工具调用）被静默吞掉
            printText(message);

            // 官方推荐的 canonical agentic loop：以 stop_reason == TOOL_USE 为循环条件
            // 见 https://platform.claude.com/docs/en/agents-and-tools/tool-use/how-tool-use-works
            while (isToolUse(message)) {
                MessageParam toolResultMessage = executeToolCalls(message);
                appendMessage(toolResultMessage);

                try {
                    message = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                appendMessage(message.toParam());
                recordUsageIfNeeded(message);
                printText(message);
            }
            // 非 TOOL_USE 退出：检查是否被截断
            warnIfTruncated(message);
        }
    }

    /**
     * 打印 assistant 消息中的所有文本块。空文本跳过。
     */
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

    /**
     * 判断是否需要继续工具循环。
     * <p>官方推荐以 {@code stop_reason == TOOL_USE} 而非遍历 content，
     * 因为模型可能输出空 tool_use 或被 max_tokens 截断，stop_reason 更可靠。
     */
    private boolean isToolUse(Message message) {
        return message.stopReason()
                .map(StopReason.TOOL_USE::equals)
                .orElse(false);
    }

    /** 命中 MAX_TOKENS 时打印警告，避免静默截断工具调用导致死循环。 */
    private void warnIfTruncated(Message message) {
        boolean maxTokens = message.stopReason()
                .map(StopReason.MAX_TOKENS::equals)
                .orElse(false);
        if (maxTokens) {
            System.out.printf("\u001b[91m[警告]\u001b[0m 本轮回复被 max_tokens=%d 截断，工具调用可能不完整。建议调大 MAX_TOKENS。%n",
                    MAX_TOKENS);
        }
    }

    /**
     * 执行一轮 assistant 回复中的所有工具调用，返回封装好的 user 角色 tool_result 消息。
     * <p>文本块已由 {@link #printText(Message)} 处理，这里只负责工具；
     * 找不到工具或执行抛异常都会被标记为 isError=true 回灌给模型。
     */
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
     */
    private void recordUsageIfNeeded(Message message) {
        if (sessionManager != null && message.usage() != null) {
            sessionManager.recordUsage(message.usage());
        }
    }

    /**
     * 向 messageParams 追加一条消息；若设置了 {@link SessionManager}，
     * 同步持久化。所有需要记录历史的追加都应走此方法。
     */
    protected void appendMessage(MessageParam param) {
        messageParams.add(param);
        if (sessionManager != null) {
            sessionManager.onMessageAppended(param);
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
                appendMessage(MessageParam.builder()
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
            // 把 JsonValue 落到 Jackson JsonNode 后再组装，避免直接序列化 SDK 内部包装类型
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.set("input", input.convert(JsonNode.class));
            root.put("tool_name", tool.getName());
            return tool.getFunction().apply(root.toString());
        } else {
            // 工具自定义了入参 POJO 类型：依赖其 toString() 返回 JSON
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

        // 添加命令提示
        if (sessionManager != null) {
            String cmdHint = "\n\n特殊命令（不发送给模型）：/stats - 查看会话统计，/usage - 查看token使用明细，/exit - 退出程序";
            messageBuilder.system(systemPrompt == null ? cmdHint : systemPrompt + cmdHint);
        }

        messageBuilder.maxTokens(MAX_TOKENS);
        messageBuilder.temperature(TEMPERATURE);

        MessageCreateParams params = messageBuilder.build();
        return client.messages().create(params);
    }
}
