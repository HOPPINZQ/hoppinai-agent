package com.hoppinzq.agent.tool.compact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.session.MessageConverter;
import com.hoppinzq.agent.session.SessionBlock;
import com.hoppinzq.agent.session.SessionMessage;
import lombok.extern.slf4j.Slf4j;

import com.hoppinzq.agent.context.SessionContextHolder;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 上下文压缩器 - 压缩策略（Web 版）
 *
 * <p>协议中立：公开 API 面向 {@link LlmMessage}；内部把消息转换为
 * {@link SessionMessage}/{@link SessionBlock}（块语义与旧版一致）完成压缩手术；
 * LLM 摘要走 {@link LlmProvider}。
 *
 * @author hoppinzq
 */
@Slf4j
public class ContextCompactor {
    private final LlmProvider provider;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MessageConverter converter = new MessageConverter();

    public ContextCompactor(LlmProvider provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    private List<SessionMessage> toSessionMessages(List<LlmMessage> messages) {
        List<SessionMessage> out = new ArrayList<>(messages.size());
        for (LlmMessage m : messages) {
            out.add(converter.toSessionMessage(m));
        }
        return out;
    }

    private List<LlmMessage> toLlmMessages(List<SessionMessage> messages) {
        List<LlmMessage> out = new ArrayList<>(messages.size());
        for (SessionMessage sm : messages) {
            out.addAll(converter.toLlmMessages(sm));
        }
        return out;
    }

    /**
     * 估算消息列表的token数量
     * <p>
     * 说明：
     * - 使用粗略估算方法：约4个字符 ≈ 1个token
     * - 将消息列表序列化为JSON后计算长度
     * - 用于判断是否需要触发自动压缩
     *
     * @param messages 要估算的消息列表
     * @return 估算的token数量
     */
    public static int estimateTokens(List<LlmMessage> messages) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(messages);
            return json.length() / 4;
        } catch (Exception e) {
            // 序列化失败时返回0，表示无法估算
            return 0;
        }
    }

    /**
     * 微压缩 - 旧 tool_result 替换为占位符（保留最近 KEEP_RECENT 条）
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<LlmMessage> microCompact(List<LlmMessage> messages) {
        List<SessionMessage> sms = toSessionMessages(messages);

        // 【阶段1：信息收集】
        List<ToolResultInfo> toolResults = new ArrayList<>();
        // 工具ID -> 工具名称映射，用于生成占位符
        Map<String, String> toolNameMap = new HashMap<>();

        for (int i = 0; i < sms.size(); i++) {
            SessionMessage msg = sms.get(i);
            try {
                if ("assistant".equals(msg.getRole())) {
                    // 助手消息：收集工具调用的 ID 和名称映射
                    if (msg.getBlocks() != null) {
                        for (SessionBlock block : msg.getBlocks()) {
                            if ("tool_use".equals(block.getType())) {
                                toolNameMap.put(block.getToolUseId(), block.getToolName());
                            }
                        }
                    }
                } else if ("user".equals(msg.getRole())) {
                    // 用户消息：收集工具结果的位置信息
                    if (msg.getBlocks() != null) {
                        List<SessionBlock> contents = msg.getBlocks();
                        for (int j = 0; j < contents.size(); j++) {
                            SessionBlock block = contents.get(j);
                            if ("tool_result".equals(block.getType())) {
                                toolResults.add(new ToolResultInfo(i, j, block));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[微压缩] 处理第 " + i + " 条消息时出错: " + e.getMessage());
                // 跳过此条消息，继续处理下一条
            }
        }

        // 【阶段2：判断是否需要压缩】
        if (toolResults.size() <= KEEP_RECENT) {
            return messages;
        }

        // 【阶段3：执行压缩】
        List<SessionMessage> newMessages = new ArrayList<>();
        int totalResults = toolResults.size();
        int thresholdIndex = totalResults - KEEP_RECENT;
        int currentResultIndex = 0;

        // 【阶段4：重建消息列表】
        for (SessionMessage msg : sms) {
            if ("user".equals(msg.getRole())) {
                if (msg.getBlocks() == null) {
                    newMessages.add(msg);
                    continue;
                }
                List<SessionBlock> newBlocks = new ArrayList<>();
                boolean changed = false;

                for (SessionBlock block : msg.getBlocks()) {
                    if ("tool_result".equals(block.getType())) {
                        if (currentResultIndex < thresholdIndex) {
                            // 将旧的工具执行结果替换为简洁的占位符
                            String toolId = block.getToolUseId();
                            String toolName = toolNameMap.getOrDefault(toolId, "unknown");
                            newBlocks.add(SessionBlock.builder()
                                    .type("tool_result")
                                    .toolUseId(toolId)
                                    .toolResultContent("[已执行: " + toolName + "]")
                                    .isError(false)
                                    .build());
                            changed = true;
                        } else {
                            newBlocks.add(block);
                        }
                        currentResultIndex++;
                    } else {
                        newBlocks.add(block);
                    }
                }

                if (changed && !newBlocks.isEmpty()) {
                    newMessages.add(SessionMessage.builder()
                            .role("user")
                            .blocks(newBlocks)
                            .build());
                } else {
                    newMessages.add(msg);
                }
            } else {
                newMessages.add(msg);
            }
        }

        return toLlmMessages(newMessages);
    }

    /**
     * 自动压缩 - 完整对话压缩
     *
     * 功能说明：
     * - 将完整对话历史保存到 TRANSCRIPT_DIR 目录
     * - 请求LLM生成对话摘要（包含已完成的工作、当前状态、关键决策）
     * - 用摘要消息替换原始消息列表，实现上下文压缩
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表（仅包含摘要消息）
     */
    public List<LlmMessage> autoCompact(List<LlmMessage> messages) {
        // 【步骤1】保存完整对话记录到磁盘（JSONL格式，每行一个消息）
        try {
            String sessionId = SessionContextHolder.get();
            String dirName = (sessionId != null && !sessionId.isEmpty()) ? sessionId : "default";
            Path path = Path.of(TRANSCRIPT_DIR, dirName);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            Path transcriptPath = path.resolve("transcript_" + System.currentTimeMillis() + ".jsonl");
            ObjectMapper mapper = new ObjectMapper();
            List<String> lines = new ArrayList<>();
            for (SessionMessage msg : toSessionMessages(messages)) {
                lines.add(mapper.writeValueAsString(msg));
            }
            Files.write(transcriptPath, lines);
            System.out.println("[对话记录已保存: " + transcriptPath + "]");

            // 【步骤2】生成对话摘要
            String conversationText = mapper.writeValueAsString(toSessionMessages(messages));
            if (conversationText.length() > 80000) {
                conversationText = conversationText.substring(0, 80000);
            }

            String summaryPrompt = "请总结这段对话以保持上下文连贯性。需要包含：\n" +
                    "1) 已完成的工作内容\n" +
                    "2) 当前状态\n" +
                    "3) 做出的关键决策\n" +
                    "请简洁明了，但保留关键细节。\n\n" + conversationText;

            String summary = completeText(summaryPrompt, 2000);

            // 【步骤3】构建压缩后的新消息列表
            List<LlmMessage> newHistory = new ArrayList<>();
            newHistory.add(LlmMessage.user("[对话已压缩。完整记录: " + transcriptPath + "]\n\n" + summary));
            newHistory.add(LlmMessage.assistant("收到。我已从摘要中获取了上下文。继续工作。", null));

            return newHistory;

        } catch (IOException e) {
            // 压缩失败时返回原始消息列表，确保对话不中断
            System.err.println("自动压缩时出错: " + e.getMessage());
            return messages;
        }
    }

    /**
     * 无工具的纯文本补全，返回 assistant 文本（失败抛异常由调用方处理）
     */
    private String completeText(String prompt, int maxTokens) {
        LlmResponse response = provider.complete(LlmRequest.builder()
                .model(MODEL)
                .messages(List.of(LlmMessage.user(prompt)))
                .maxTokens(maxTokens)
                .build());
        String text = response.getMessage() == null ? null : response.getMessage().getText();
        return text == null ? "" : text;
    }

    /**
     * 工具结果位置记录（消息索引、块索引、结果块）
     */
    private record ToolResultInfo(int msgIdx, int partIdx, SessionBlock result) {
    }
}
