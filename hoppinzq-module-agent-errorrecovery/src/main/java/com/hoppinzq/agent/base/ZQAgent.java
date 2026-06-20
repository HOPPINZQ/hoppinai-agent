package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.recovery.RecoveryState;
import com.hoppinzq.agent.tool.recovery.RetryWrapper;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类（errorrecovery 模块本地副本）。
 * <p>
 * 与 module-02 的差异：chatMessage 改为通过 {@link RetryWrapper} 调用，
 * 三条恢复路径：输出截断升级 / prompt_too_long 紧急压缩 / 瞬态错误指数退避。
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
    /** 重试包装器；为 null 表示走原始无恢复路径 */
    private RetryWrapper retryWrapper;
    /** 会话级恢复状态；首次需要时按 model/fallbackModel 创建 */
    private RecoveryState recoveryState;
    /** 兜底模型；为 null 不切换 */
    private String fallbackModel;
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

    /**
     * 包装后的 LLM 调用。如启用 RetryWrapper，先走带恢复策略的 {@link RetryWrapper#call}；
     * 否则与 module-02 行为一致。
     */
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

        if (retryWrapper == null) {
            // 原始路径（无恢复）
            return client.messages().create(baseParamsBuilder(messageParams, anthropicTools, model).build());
        }

        // 懒初始化 RecoveryState
        if (recoveryState == null) {
            recoveryState = new RecoveryState(model, fallbackModel);
        }
        return retryWrapper.call(
                state -> baseParamsBuilder(messageParams, anthropicTools, state.getModel()),
                recoveryState,
                messageParams);
    }

    private MessageCreateParams.Builder baseParamsBuilder(List<MessageParam> messageParams,
                                                          List<ToolUnion> anthropicTools,
                                                          String useModel) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(useModel)
                .messages(messageParams)
                .tools(anthropicTools);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            b.system(systemPrompt);
        }
        b.temperature(TEMPERATURE);
        // 注意：maxTokens 由 RetryWrapper / RecoveryState 控制；
        // 这里给一个默认值，wrapper 会覆盖。
        if (recoveryState != null) {
            b.maxTokens(recoveryState.getMaxTokens());
        } else {
            b.maxTokens(MAX_TOKENS);
        }
        return b;
    }
}
