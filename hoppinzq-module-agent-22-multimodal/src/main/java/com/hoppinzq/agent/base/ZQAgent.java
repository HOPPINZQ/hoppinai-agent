package com.hoppinzq.agent.base;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.tool.MessageContent;
import com.hoppinzq.agent.tool.ToolDefinition;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.MAX_TOKENS;
import static com.hoppinzq.agent.constant.AIConstants.SUPPORTS_VISION;
import static com.hoppinzq.agent.constant.AIConstants.TEMPERATURE;

/**
 * s22 多模态 ZQAgent。
 * <p>
 * 与 s01 的 ZQAgent 区别：
 * <ul>
 *   <li>{@link #messageParams} 仍为 {@code List<MessageParam>}（Anthropic SDK 要求），
 *       但提供 {@link #addUserMessage(List)} 接受 {@code List<MessageContent>}，
 *       内部根据 {@link com.hoppinzq.agent.constant.AIConstants#SUPPORTS_VISION} 组装
 *       text block 或 image block。</li>
 *   <li>命令行循环里，用户输入可以追加图片（暂时用占位语法 {@code /img path} 上传图片）。</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
public class ZQAgent {
    protected final AnthropicClient client;
    protected final List<MessageParam> messageParams = new ArrayList<>();
    private final Scanner scanner;
    private final String model;
    private final List<ToolDefinition> tools;
    private final ObjectMapper mapper = new ObjectMapper();
    private String systemPrompt;

    public ZQAgent(AnthropicClient client, String model, List<ToolDefinition> tools) {
        this.client = client;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
    }

    /** 添加一条多模态用户消息。 */
    public void addUserMessage(List<MessageContent> contents) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (MessageContent c : contents) {
            switch (c.getType()) {
                case "text" -> blocks.add(ContentBlockParam.ofText(
                        TextBlockParam.builder().text(c.getText()).build()));
                case "image" -> {
                    if (SUPPORTS_VISION) {
                        // 解析 base64 → image block
                        blocks.add(ContentBlockParam.ofImage(
                                ImageBlockParam.builder()
                                        .source(ImageBlockParam.Source.builder()
                                                .type("base64")
                                                .mediaType(c.getMediaType())
                                                .data(c.getData())
                                                .build())
                                        .build()));
                    } else {
                        // 降级为文本描述
                        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder()
                                .text("[图片: " + c.getMediaType() + "] 当前模型不支持 vision。")
                                .build()));
                    }
                }
                case "file" -> blocks.add(ContentBlockParam.ofText(
                        TextBlockParam.builder()
                                .text("[文件: " + c.getFilename() + "]\n" + c.getText())
                                .build()));
                default -> blocks.add(ContentBlockParam.ofText(
                        TextBlockParam.builder().text(String.valueOf(c)).build()));
            }
        }
        MessageParam.Content content = MessageParam.Content.ofBlockParams(blocks);
        messageParams.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(content)
                .build());
    }

    public void run() {
        System.out.println("开始对话吧（多模态）。输入 '/img <path>' 可附图片，输入 '退出' 结束。");
        while (true) {
            System.out.print("\u001b[94m你\u001b[0m: ");
            String userInput;
            try {
                userInput = scanner.nextLine();
            } catch (NoSuchElementException | IllegalStateException e) {
                break;
            }
            if (userInput == null || userInput.isEmpty()) continue;
            if ("退出".equals(userInput.trim()) || "exit".equalsIgnoreCase(userInput.trim())) {
                System.out.println("再见 👋");
                break;
            }

            List<MessageContent> contents = new ArrayList<>();
            // 简单解析 /img 前缀：附加图片
            if (userInput.startsWith("/img ")) {
                String[] parts = userInput.substring(5).trim().split("\\s+", 2);
                String imgPath = parts[0];
                String caption = parts.length > 1 ? parts[1] : "";
                try {
                    java.nio.file.Path full = java.nio.file.Path.of(System.getProperty("user.dir")).resolve(imgPath).normalize();
                    byte[] bytes = java.nio.file.Files.readAllBytes(full);
                    String mime = java.nio.file.Files.probeContentType(full);
                    if (mime == null || !mime.startsWith("image/")) {
                        contents.add(MessageContent.text("文件不是图片: " + imgPath));
                    } else {
                        contents.add(MessageContent.image(mime,
                                java.util.Base64.getEncoder().encodeToString(bytes)));
                        if (!caption.isEmpty()) {
                            contents.add(MessageContent.text(caption));
                        }
                    }
                } catch (Exception e) {
                    contents.add(MessageContent.text("读取图片失败: " + e.getMessage()));
                }
            } else {
                contents.add(MessageContent.text(userInput));
            }
            addUserMessage(contents);

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
                        String result = content.text().map(TextBlock::text).orElse("");
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
                                    toolResult = invokeTool(tool, toolUse._input());
                                    // 若工具返回的是 MessageContent JSON，打印一行简短描述
                                    System.out.printf("\u001b[92m结果\u001b[0m: %s%n", summarize(toolResult));
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

                if (!hasToolUse) break;

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

    private String invokeTool(ToolDefinition tool, JsonValue input) throws Exception {
        if (tool.getType() == null) {
            Optional<Map<String, JsonValue>> object = input.asObject();
            if (object.isPresent()) {
                Map<String, JsonValue> map = object.get();
                Map<String, Object> callTool = new HashMap<>();
                callTool.put("input", map);
                callTool.put("tool_name", tool.getName());
                return tool.getFunction().apply(new ObjectMapper().writeValueAsString(callTool));
            }
            throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数转换失败");
        }
        return tool.getFunction().apply(Objects.requireNonNull(input.convert(tool.getType())).toString());
    }

    /** 工具结果若是 base64 JSON，打印一行截断描述。 */
    private String summarize(String result) {
        if (result == null) return "";
        if (result.startsWith("{\"type\":\"image\"") || result.contains("\"data\":\"")) {
            return "[image base64, " + result.length() + " chars]";
        }
        if (result.length() > 200) {
            return result.substring(0, 200) + "... (" + result.length() + " chars)";
        }
        return result;
    }

    protected Message chatMessage(List<MessageParam> messageParams) {
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
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .messages(messageParams)
                .tools(anthropicTools)
                .maxTokens(MAX_TOKENS)
                .temperature(TEMPERATURE);
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            b.system(systemPrompt);
        }
        return client.messages().create(b.build());
    }
}
