package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.memory.MemoryExtractor;
import com.hoppinzq.agent.tool.memory.MemorySelector;
import com.hoppinzq.agent.tool.memory.MemoryStore;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类（memory 模块本地副本）。
 * <p>
 * 与 module-02 的差异：
 * <ul>
 *   <li>每轮 LLM 调用前，用 {@link MemorySelector} 从索引里选相关记忆，追加到 systemPrompt 末尾</li>
 *   <li>每轮工具循环结束后，用 {@link MemoryExtractor} 抽取新记忆并写入 {@link MemoryStore}</li>
 *   <li>记忆文件数 ≥ 阈值时触发 consolidate 提示</li>
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
    /** 持久记忆三件套；全为 null 表示不启用记忆 */
    private MemoryStore memoryStore;
    private MemorySelector memorySelector;
    private MemoryExtractor memoryExtractor;
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

            // 每轮 LLM 调用前：选相关记忆，注入到 system prompt
            String effectiveSystem = systemPrompt;
            if (memorySelector != null) {
                String memoryBlock = memorySelector.selectAndFormat(userInput);
                if (!memoryBlock.isEmpty()) {
                    effectiveSystem = (systemPrompt == null ? "" : systemPrompt + "\n\n")
                            + "## 相关记忆\n" + memoryBlock;
                    System.out.println("\u001b[95m[memory]\u001b[0m 已注入相关记忆");
                }
            }

            MessageParam userMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(userInput)
                    .build();
            messageParams.add(userMessage);

            Message message;
            try {
                message = chatMessage(messageParams, effectiveSystem);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                continue;
            }
            messageParams.add(message.toParam());

            String lastAssistantText = "";

            while (true) {
                List<ContentBlockParam> toolResults = new ArrayList<>();
                boolean hasToolUse = false;

                for (ContentBlock content : message.content()) {
                    if (content.isText()) {
                        Optional<TextBlock> text = content.text();
                        String result = text.map(TextBlock::text).orElse("");
                        lastAssistantText = result;
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
                    message = chatMessage(messageParams, effectiveSystem);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                messageParams.add(message.toParam());
            }

            // 每轮工具循环后：抽取记忆并落盘
            if (memoryExtractor != null) {
                try {
                    int saved = memoryExtractor.extractAndSave(userInput, lastAssistantText);
                    if (saved > 0 && memoryStore != null) {
                        memoryStore.consolidateIfNeeded();
                    }
                } catch (Exception e) {
                    System.out.printf("\u001b[91m[memory extract 失败]\u001b[0m %s%n", e.getMessage());
                }
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

    /** chatMessage 接受当前生效的系统提示词（每轮动态变化） */
    protected Message chatMessage(List<MessageParam> messageParams, String effectiveSystem){
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

        if(effectiveSystem != null && !effectiveSystem.isEmpty()){
            messageBuilder.system(effectiveSystem);
        }

        messageBuilder.maxTokens(MAX_TOKENS);
        messageBuilder.temperature(TEMPERATURE);

        MessageCreateParams params = messageBuilder.build();
        return client.messages().create(params);
    }
}
