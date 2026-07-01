package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.cron.CronScheduler;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类（cron 模块本地副本）。
 * <p>
 * 与 module-02 的差异：
 * <ol>
 *   <li>构造时启动 {@link CronScheduler}，主循环每轮 LLM 调用前 {@link CronScheduler#consumeQueue()}</li>
 *   <li>非空 prompt 作为 system reminder 注入 user message，让模型知道是定时触发</li>
 *   <li>LLM 调用 + 工具执行期间 markBusy，等下一次 user input 时 markIdle</li>
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
            appendMessage(message.toParam());
            printText(message);

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
                printText(message);
            }
            warnIfTruncated(message);

            // 一轮结束：切回 idle，让调度线程有机会把队列里的 prompt 投递出来
            if (cronScheduler != null) {
                cronScheduler.markIdle();
            }
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
     * 向 messageParams 追加一条消息；若设置了 {@link SessionManager}，
     * 同步持久化。所有需要记录历史的追加都应走此方法。
     */
    protected void appendMessage(MessageParam param) {
        messageParams.add(param);
        if (sessionManager != null) {
            sessionManager.onMessageAppended(param);
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
