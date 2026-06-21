package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.permission.Decision;
import com.hoppinzq.agent.tool.permission.PermissionChecker;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类（permission 模块本地副本）。
 * <p>
 * 与 module-02 的差异：在工具执行循环前插入 {@link PermissionChecker} 做三重闸门检查，
 * DENY 时把拒绝原因作为 isError=true 的 ToolResult 回灌给模型，避免真实执行。
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
    /** 工具执行前的权限闸门；为 null 表示不启用权限检查 */
    private PermissionChecker permissionChecker;
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

                        String toolResult = null;
                        Exception toolError = null;
                        boolean toolFound = false;

                        for (ToolDefinition tool : tools) {
                            if (tool.getName().equals(toolUse.name())) {
                                toolFound = true;
                                try {
                                    JsonValue input = toolUse._input();
                                    // === 权限闸门 ===
                                    Decision decision = checkPermission(toolUse.name(), input);
                                    if (decision != null && decision.getType() == Decision.Type.DENY) {
                                        System.out.printf("\u001b[91m[权限拒绝]\u001b[0m: %s%n", decision.getReason());
                                        toolError = new Exception("权限拒绝：" + decision.getReason());
                                        break;
                                    }
                                    toolResult = invokeTool(tool, input);
                                    System.out.printf("\u001b[92m结果\u001b[0m: %s%n", toolResult);
                                } catch (Exception e) {
                                    toolError = e;
                                    System.out.printf("\u001b[91m错误\u001b[0m: %s%n", e.getMessage());
                                    e.printStackTrace();
                                }
                                break;
                            }
                        }

                        if (!toolFound) {
                            toolError = new Exception("工具 '" + toolUse.name() + "' 没有找到");
                            System.out.printf("\u001b[91m错误\u001b[0m: %s%n", toolError.getMessage());
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
        }
    }

    /**
     * 把 JsonValue 序列化为字符串供 {@link PermissionChecker} 做正则匹配。
     * 这里优先用工具类型反序列化得到的 POJO 的 toString（如 BashInput.toString 返回 JSON），
     * 退化时用 JsonValue 自身的 toString。
     */
    private String serializeInput(ToolDefinition tool, JsonValue input) {
        try {
            if (tool.getType() != null) {
                Object converted = input.convert(tool.getType());
                if (converted != null) {
                    return converted.toString();
                }
            }
        } catch (Exception ignore) {
            // fall through
        }
        return input == null ? "" : input.toString();
    }

    private Decision checkPermission(String toolName, JsonValue input) {
        if (permissionChecker == null) {
            return null;
        }
        for (ToolDefinition tool : tools) {
            if (tool.getName().equals(toolName)) {
                String inputJson = serializeInput(tool, input);
                return permissionChecker.check(toolName, inputJson);
            }
        }
        return null;
    }

    protected void onToolExecution(List<ContentBlockParam> toolResults) {

    }

    private String invokeTool(ToolDefinition tool, JsonValue input) throws Exception {
        if(tool.getType() == null){
            Optional<Map<String, JsonValue>> object = input.asObject();
            if(object.isPresent()){
                Map<String, JsonValue> map = object.get();
                Map<String, Object> callTool = new HashMap<>();
                // todo : 这里没有处理嵌套的情况，需要进一步优化
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
