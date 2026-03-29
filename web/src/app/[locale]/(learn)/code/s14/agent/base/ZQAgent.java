package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.tool.ToolDefinition;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hoppinzq.agent.constant.AIConstants.MAX_TOKENS;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;
import static com.hoppinzq.agent.constant.AIConstants.TEMPERATURE;
import static com.hoppinzq.agent.constant.AIConstants.REACT_ENABLE;

/**
 * 智能体基类
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

            while (true) {
                Message message;
                try {
                    message = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }

                String resultText = "";
                for (ContentBlock content : message.content()) {
                    if (content.isText()) {
                        resultText += content.text().map(TextBlock::text).orElse("");
                    }
                }

                if (REACT_ENABLE) {
                    messageParams.add(MessageParam.builder()
                            .role(MessageParam.Role.ASSISTANT)
                            .content(resultText)
                            .build());

                    if (resultText.contains("Action:")) {
                        System.out.println();
                        System.out.print("\u001b[95m***************************************************************************\u001b[0m\n");
                        System.out.printf("\u001b[95mAI思考的中间过程（忽略）\u001b[0m:\n%s%n\n", resultText.replaceAll("(?s)\nObservation:.*", "").trim());
                        
                        String observation = null;
                        try {
                            String action = null;
                            Pattern actionPattern = Pattern.compile("Action:\\s*([^\\n]+)");
                            Matcher actionMatcher = actionPattern.matcher(resultText);
                            if (actionMatcher.find()) {
                                action = actionMatcher.group(1).trim();
                            }
                            
                            String actionInputStr = null;
                            Pattern inputPattern = Pattern.compile("Action Input:\\s*(\\{.*?\\})(?=\\s*\n|$)", Pattern.DOTALL);
                            Matcher inputMatcher = inputPattern.matcher(resultText);
                            if (inputMatcher.find()) {
                                actionInputStr = inputMatcher.group(1);
                            }
                            
                            if (action != null && actionInputStr != null) {
                                ToolDefinition targetTool = null;
                                for (ToolDefinition tool : tools) {
                                    if (tool.getName().equals(action)) {
                                        targetTool = tool;
                                        break;
                                    }
                                }
                                
                                if (targetTool != null) {
                                    Map<String, Object> inputMap = OBJECT_MAPPER.readValue(actionInputStr, Map.class);
                                    JsonValue inputJson = JsonValue.from(inputMap);
                                    observation = invokeTool(targetTool, inputJson);
                                } else {
                                    observation = "未知的工具: " + action;
                                }
                            } else {
                                observation = "没有找到Action或Action Input";
                            }
                        } catch (Exception e) {
                            observation = "执行异常: " + e.getMessage();
                        }
                        
                        System.out.println("Observation: " + observation);
                        System.out.print("\u001b[95m***************************************************************************\u001b[0m\n");
                        System.out.println();
                        
                        messageParams.add(MessageParam.builder()
                                .role(MessageParam.Role.USER)
                                .content("Observation: " + observation)
                                .build());
                    } else {
                        System.out.printf("\u001b[93mAI\u001b[0m: %s%n", resultText);
                        break;
                    }
                } else {
                    messageParams.add(message.toParam());
                    boolean hasToolUse = false;
                    List<ContentBlockParam> toolResults = new ArrayList<>();
                    
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
                }
            }
        }
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
        MessageCreateParams.Builder messageBuilder = MessageCreateParams.builder()
                .model(model)
                .messages(messageParams);

        if(systemPrompt != null && !systemPrompt.isEmpty()){
            messageBuilder.system(systemPrompt);
        }
        if (!REACT_ENABLE && tools != null && !tools.isEmpty()) {
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
            messageBuilder.tools(anthropicTools);
        }
        messageBuilder.maxTokens(MAX_TOKENS);
        messageBuilder.temperature(TEMPERATURE);

        MessageCreateParams params = messageBuilder.build();
        return client.messages().create(params);
    }
}
