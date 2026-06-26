package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.prompt.AgentContext;
import com.hoppinzq.agent.tool.prompt.PromptAssembler;
import lombok.Data;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类（sysprompt 模块本地副本）。
 * <p>
 * 与 module-02 的差异：去掉 setSystemPrompt 一次性赋值，
 * 每轮 LLM 调用前用 {@link PromptAssembler#get(AgentContext)} 动态装配系统提示。
 * AgentContext 在每轮重新构造（检测 MEMORY.md 是否存在、当前工具列表），
 * 同一 context 走缓存避免重复渲染。
 *
 * @author hoppinzq
 */
@Data
public class ZQAgent {
    private final Scanner scanner;
    private final String model;
    protected final AnthropicClient client;
    private final List<ToolDefinition> tools;
    protected final List<MessageParam> messageParams = new ArrayList<>();
    /** 系统提示词装配器；为 null 表示不启用动态装配 */
    private PromptAssembler promptAssembler;
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

            // 每轮装配系统提示词（context hash 变化才重新渲染）
            String systemPrompt = null;
            if (promptAssembler != null) {
                AgentContext ctx = buildContext(userInput);
                systemPrompt = promptAssembler.get(ctx);
            }

            MessageParam userMessage = MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(userInput)
                    .build();
            messageParams.add(userMessage);

            Message message;
            try {
                message = chatMessage(messageParams, systemPrompt);
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

                        toolResults.add(ContentBlockParam.ofToolResult(
                                ToolResultBlockParam.builder()
                                        .toolUseId(toolUse.id())
                                        .content(toolError != null ? toolError.getMessage() : toolResult)
                                        .isError(toolError != null)
                                        .build()
                        ));
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
                    message = chatMessage(messageParams, systemPrompt);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                messageParams.add(message.toParam());
            }
        }
    }

    private AgentContext buildContext(String userInput) {
        List<String> names = new ArrayList<>();
        for (ToolDefinition t : tools) {
            names.add(t.getName());
        }
        boolean memoryExists = Files.isRegularFile(Paths.get(ROOT, "MEMORY.md"));
        return AgentContext.builder()
                .workspace(ROOT)
                .osName(System.getProperty("os.name").toLowerCase())
                .toolNames(names)
                .memoryEnabled(memoryExists)
                .userPrompt(userInput)
                .build();
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
