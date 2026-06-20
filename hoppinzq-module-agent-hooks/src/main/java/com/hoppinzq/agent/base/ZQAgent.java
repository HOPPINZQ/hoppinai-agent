package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.hook.HookContext;
import com.hoppinzq.agent.tool.hook.HookEvent;
import com.hoppinzq.agent.tool.hook.HookRegistry;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类（hooks 模块本地副本）。
 * <p>
 * 与 module-02 的差异：注入 {@link HookRegistry}，在 4 个事件点触发钩子：
 * <ul>
 *   <li>{@link HookEvent#USER_PROMPT_SUBMIT} — 读到用户输入后立即触发；返回非 null 用作改写后的 prompt</li>
 *   <li>{@link HookEvent#PRE_TOOL_USE} — 工具执行前；返回非 null 跳过执行，返回值作为 ToolResult 回灌</li>
 *   <li>{@link HookEvent#POST_TOOL_USE} — 工具执行后；仅副作用（日志/统计）</li>
 *   <li>{@link HookEvent#STOP} — 本轮无工具调用、循环即将退出时触发</li>
 * </ul>
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
    /** 钩子注册中心；为 null 表示不启用钩子 */
    private HookRegistry hookRegistry;
    private String taskResult;
    private boolean taskCompleted = false;

    public ZQAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
    }

    public void run() {
        System.out.println("开始对话吧");
        while (true) {
            System.out.print("\u001b[94m你\u001b[0m: ");
            String userInput = scanner.nextLine();
            if (userInput.isEmpty()) {
                continue;
            }

            // === 事件 1：UserPromptSubmit ===
            if (hookRegistry != null) {
                String rewritten = hookRegistry.trigger(HookEvent.USER_PROMPT_SUBMIT,
                        HookContext.forPrompt(userInput));
                if (rewritten != null) {
                    System.out.printf("\u001b[90m[hook 改写 prompt]\u001b[0m %s%n", rewritten);
                    userInput = rewritten;
                }
            }

            MessageParam userMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(userInput)
                    .build();
            messageParams.add(userMessage);

            Message message;
            try {
                message = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                continue;
            }
            messageParams.add(message.toParam());

            while (true) {
                List<ContentBlockParam> toolResults = new ArrayList<>();
                boolean hasToolUse = false;

                for (ContentBlock content : message.content()) {
                    if (content.isText()) {
                        Optional<TextBlock> text = content.text();
                        String result = text.map(TextBlock::text).orElse("");
                        System.out.printf("\u001b[93mAI\u001b[0m: %s%n", result);
                    } else if (content.isToolUse()) {
                        hasToolUse = true;
                        ToolUseBlock toolUse = content.asToolUse();

                        System.out.printf("\u001b[96m工具\u001b[0m: %s(%s)%n", toolUse.name(), toolUse._input());

                        String inputJson = serializeInput(toolUse.name(), toolUse._input());
                        String toolResult = null;
                        Exception toolError = null;
                        boolean toolFound = false;
                        boolean blockedByHook = false;

                        // === 事件 2：PreToolUse ===
                        if (hookRegistry != null) {
                            String block = hookRegistry.trigger(HookEvent.PRE_TOOL_USE,
                                    HookContext.forPreTool(toolUse.name(), inputJson));
                            if (block != null) {
                                blockedByHook = true;
                                toolResult = block;
                                System.out.printf("\u001b[93m[hook 阻断]\u001b[0m %s%n", block);
                            }
                        }

                        if (!blockedByHook) {
                            for (ToolDefinition tool : tools) {
                                if (tool.getName().equals(toolUse.name())) {
                                    try {
                                        JsonValue input = toolUse._input();
                                        toolResult = invokeTool(tool, input);
                                        System.out.printf("\u001b[92m结果\u001b[0m: %s%n", toolResult);
                                    } catch (Exception e) {
                                        toolError = e;
                                        System.out.printf("\u001b[91m错误\u001b[0m: %s%n", e.getMessage());
                                        e.printStackTrace();
                                    }
                                    toolFound = true;
                                    break;
                                }
                            }
                            if (!toolFound) {
                                toolError = new Exception("工具 '" + toolUse.name() + "' 没有找到");
                                System.out.printf("\u001b[91m错误\u001b[0m: %s%n", toolError.getMessage());
                            }
                        }

                        // === 事件 3：PostToolUse ===
                        if (hookRegistry != null) {
                            hookRegistry.trigger(HookEvent.POST_TOOL_USE,
                                    HookContext.forPostTool(toolUse.name(), inputJson,
                                            toolResult != null ? toolResult
                                                    : (toolError != null ? toolError.getMessage() : ""),
                                            toolError != null));
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
                    break;
                }

                MessageParam.Content content = MessageParam.Content.ofBlockParams(toolResults);
                MessageParam toolResultMessage = MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(content)
                        .build();
                messageParams.add(toolResultMessage);
                try {
                    message = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                messageParams.add(message.toParam());
            }

            // === 事件 4：Stop ===
            if (hookRegistry != null) {
                hookRegistry.trigger(HookEvent.STOP, HookContext.forStop());
            }
        }
    }

    private String serializeInput(String toolName, JsonValue input) {
        if (input == null) {
            return "";
        }
        ToolDefinition tool = null;
        for (ToolDefinition t : tools) {
            if (t.getName().equals(toolName)) {
                tool = t;
                break;
            }
        }
        try {
            if (tool != null && tool.getType() != null) {
                Object converted = input.convert(tool.getType());
                if (converted != null) {
                    return converted.toString();
                }
            }
        } catch (Exception ignore) {
            // fall through
        }
        return input.toString();
    }

    protected void onToolExecution(List<ContentBlockParam> toolResults) {

    }

    private String invokeTool(ToolDefinition tool, JsonValue input) throws Exception {
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

    protected Message chatMessage(List<MessageParam> messageParams){
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
