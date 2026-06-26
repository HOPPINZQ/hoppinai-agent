package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    // ========================= teams 相关字段 =========================
    /** lead 的消息总线实例（其实 MessageBus 全是静态方法，这里只是为了依赖注入表达） */
    private com.hoppinzq.agent.tool.bus.MessageBus messageBus;
    /** teammate 复用的客户端（默认复用 this.client） */
    private AnthropicClient teammateClient;
    /** 派给 teammate 的工具子集 */
    private List<ToolDefinition> teammateTools;
    /** lead 名字，邮箱目录就用这个名字 */
    private String leadName = "lead";

    public ZQAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
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
                printText(message);
            }
            warnIfTruncated(message);

            // 一轮结束：切回 idle，让调度线程有机会把队列里的 prompt 投递出来
            if (cronScheduler != null) {
                cronScheduler.markIdle();
            }
            // teams：每轮结束后顺便消费一下 lead 的邮箱，把 teammate 的消息注入下一轮
            pollInbox();
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
     * 消费 lead 邮箱里的未读消息，合并成一条 user 消息注入到下一轮 LLM 调用。
     * 读即消费（{@link com.hoppinzq.agent.tool.bus.MessageBus#readInbox} 会清空文件），
     * 不会重复注入同一条消息。
     */
    private void pollInbox() {
        if (messageBus == null) {
            return;
        }
        List<com.hoppinzq.agent.tool.bus.MailboxMessage> msgs =
                com.hoppinzq.agent.tool.bus.MessageBus.readInbox(leadName);
        if (msgs.isEmpty()) {
            return;
        }
        String combined = msgs.stream()
                .map(m -> "[" + m.getFrom() + "] " + m.getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        System.out.println("收到消息: " + combined);
        MessageParam msg = MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("[teammate message] " + combined)
                .build();
        messageParams.add(msg);
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
