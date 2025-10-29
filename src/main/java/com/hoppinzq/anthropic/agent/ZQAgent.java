package com.hoppinzq.anthropic.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.anthropic.tool.ToolDefinition;
import com.hoppinzq.anthropic.tool.schema.ThoughtProcessInput;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.anthropic.constant.AIConstants.MAX_TOKENS;
import static com.hoppinzq.anthropic.constant.AIConstants.OBJECT_MAPPER;

/**
 * @author hoppinzq
 */
@Data
public class ZQAgent {
    private String systemPrompt;
    private final Scanner scanner;
    private final String model;
    private final AnthropicClient client;
    private final List<ToolDefinition> tools;
    private final List<MessageParam> messageParams = new ArrayList<>();

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
            if(systemPrompt!=null && !systemPrompt.isEmpty()){
                MessageParam systemMessage = MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(systemPrompt)
                        .build();
                messageParams.add(systemMessage);
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
                        if (result.indexOf("Thought:") + result.indexOf("Action:") + result.indexOf("Action Input:") + result.indexOf("Observation:") > -4) {
                            try {
                                ThoughtProcessInput thoughtProcessInput = new ThoughtProcessInput(result);
                                thoughtProcessInput.toolCallJson(tools, toolResults);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
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
                                    //todo : 注意下面几行代码！！！！待优化 ,处理JsonValue的逻辑是如此的丑陋
                                    if(tool.getType() == null){
                                        Optional<Map<String, JsonValue>> object = input.asObject();
                                        if(object.isPresent()){
                                            Map<String, JsonValue> map = object.get();
                                            Map<String, Object> callMcp = new HashMap<>();
                                            // todo : 这里没有处理嵌套的情况，需要进一步优化
                                            callMcp.put("input",map);
                                            callMcp.put("tool_name",tool.getName());
                                            toolResult = tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(callMcp));
                                        }else{
                                            toolError = new Exception("工具 '" + toolUse.name() + "' 传参错误："+ input);
                                        }
                                    }else{
                                        toolResult = tool.getFunction().apply(Objects.requireNonNull(input.convert(tool.getType())).toString());
                                    }
                                    System.out.printf("\u001b[92m结果\u001b[0m: %s%n", toolResult);
                                } catch (Exception e) {
                                    toolError = e;
                                    System.out.printf("\u001b[91m错误\u001b[0m: %s%n", e.getMessage());
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
                MessageParam toolResultMessage = MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(toolResults.toString())
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

    private Message chatMessage(List<MessageParam> messageParams){
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
        if(MAX_TOKENS > 0){
            messageBuilder.maxTokens(MAX_TOKENS);
        }
        MessageCreateParams params = messageBuilder.build();
        return client.messages().create(params);
    }
}