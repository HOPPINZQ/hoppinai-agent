package com.hoppinzq.agent.tool.compact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.context.SessionContextHolder;
import com.hoppinzq.agent.session.MessageConverter;
import com.hoppinzq.agent.session.SessionBlock;
import com.hoppinzq.agent.session.SessionMessage;
import lombok.extern.slf4j.Slf4j;

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
 * 上下文压缩器 - 三层压缩策略
 *
 * <p>协议中立：公开 API 面向 {@link LlmMessage}；内部把消息转换为
 * {@link SessionMessage}/{@link SessionBlock}（块语义与旧版一致，轮次原子性天然保持）
 * 完成压缩手术后转换回中立消息。LLM 摘要与 token 计数均走 {@link LlmProvider}。
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

    // ============================== 列表形态转换 ==============================

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
     * Layer 1: 微压缩 - 工具结果选择性替换
     * <p>
     * 功能说明：
     * - 遍历所有消息，收集工具调用和工具结果的位置信息
     * - 将旧的工具执行结果替换为简洁的占位符（如"[已执行: read_file]"）
     * - 保留最近KEEP_RECENT个工具结果不变，确保最近的工作上下文完整
     * <p>
     * 执行时机：
     * - 每次调用LLM API前自动执行
     * - 静默执行，对用户透明
     * - 高频执行以控制上下文大小
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<LlmMessage> microCompact(List<LlmMessage> messages) {
        List<SessionMessage> sms = toSessionMessages(messages);

        // 【阶段1：信息收集】
        // 收集所有工具结果的位置信息（消息索引、内容块索引、结果数据）
        List<ToolResultInfo> toolResults = new ArrayList<>();

        // 建立工具ID到工具名称的映射，用于后续生成占位符
        Map<String, String> toolNameMap = new HashMap<>();

        // 遍历所有消息，构建工具结果索引和工具名称映射
        for (int i = 0; i < sms.size(); i++) {
            SessionMessage msg = sms.get(i);
            try {
                if ("assistant".equals(msg.getRole())) {
                    // 处理助手消息：收集所有工具调用的ID和名称映射
                    if (msg.getBlocks() != null) {
                        for (SessionBlock block : msg.getBlocks()) {
                            if ("tool_use".equals(block.getType())) {
                                toolNameMap.put(block.getToolUseId(), block.getToolName());
                            }
                        }
                    }
                } else if ("user".equals(msg.getRole())) {
                    // 处理用户消息：收集所有工具结果的位置信息
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
                // 处理消息时出错，打印错误信息并跳过该消息继续处理
                System.err.println("[微压缩] 处理第 " + i + " 条消息时出错: " + e.getMessage());
                // 跳过此条消息，继续处理下一条
                continue;
            }
        }

        // 【阶段2：判断是否需要压缩】
        // 如果工具结果数量不超过保留数量，则无需压缩
        if (toolResults.size() <= KEEP_RECENT) {
            return messages;
        }

        // 【阶段3：执行压缩】
        List<SessionMessage> newMessages = new ArrayList<>();
        int totalResults = toolResults.size();
        // 计算压缩阈值索引：在此索引之前的工具结果都将被替换为占位符
        int thresholdIndex = totalResults - KEEP_RECENT;

        // 当前遍历到的工具结果索引（从0开始递增）
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
                            // 【压缩处理】将旧的工具执行结果替换为简洁的占位符
                            String toolId = block.getToolUseId();
                            String toolName = toolNameMap.getOrDefault(toolId, "unknown");

                            // 占位符格式：[已执行: 工具名称]
                            // 这样可以保留工具调用历史，同时大幅减少token占用
                            newBlocks.add(SessionBlock.builder()
                                    .type("tool_result")
                                    .toolUseId(toolId)
                                    .toolResultContent("[已执行: " + toolName + "]")
                                    .isError(false)
                                    .build());
                            changed = true;
                        } else {
                            // 保留最近的工具结果（未被压缩的）
                            newBlocks.add(block);
                        }
                        currentResultIndex++;
                    } else {
                        // 非工具结果内容（如文本）直接保留
                        newBlocks.add(block);
                    }
                }

                if (changed) {
                    // 【消息重构】检测到内容被压缩，需要重新构建消息对象
                    if (!newBlocks.isEmpty()) {
                        newMessages.add(SessionMessage.builder()
                                .role("user")
                                .blocks(newBlocks)
                                .build());
                    } else {
                        // 边界情况处理：如果压缩后newBlocks为空，保留原始消息
                        // 这种情况理论上不应发生，作为防御性编程处理
                        newMessages.add(msg);
                    }
                } else {
                    // 内容未改变，直接添加原消息
                    newMessages.add(msg);
                }
            } else {
                newMessages.add(msg);
            }
        }

        return toLlmMessages(newMessages);
    }

    /**
     * Layer 2: 自动压缩 - 完整对话压缩
     * <p>
     * 功能说明：
     * - 将完整对话历史保存到磁盘的 .transcript/ 目录
     * - 请求LLM生成对话摘要（包含已完成的工作、当前状态、关键决策）
     * - 用摘要消息替换原始消息列表，实现上下文压缩
     * <p>
     * 触发条件：
     * - 当估算的token数量超过 TOKEN_THRESHOLD 时自动触发
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表（仅包含摘要消息）
     */
    public List<LlmMessage> autoCompact(List<LlmMessage> messages) {
        // 【步骤1】保存完整对话记录到磁盘（JSONL格式，每行一个消息）
        try {
            String sessionId = SessionContextHolder.get();
            String dirName = (sessionId != null && !sessionId.isEmpty()) ? sessionId : "default";
            Path path = Paths.get(TRANSCRIPT_DIR, dirName);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            // 使用时间戳生成唯一的transcript文件名
            Path transcriptPath = path.resolve("transcript_" + System.currentTimeMillis() + ".jsonl");
            ObjectMapper mapper = new ObjectMapper();
            List<String> lines = new ArrayList<>();
            for (SessionMessage msg : toSessionMessages(messages)) {
                lines.add(mapper.writeValueAsString(msg));
            }
            Files.write(transcriptPath, lines);
            System.out.println("[对话记录已保存: " + transcriptPath + "]");

            // 【步骤2】生成对话摘要
            // 将消息列表转换为文本（限制在80000字符以内以避免超出API限制）
            String conversationText = mapper.writeValueAsString(toSessionMessages(messages));
            if (conversationText.length() > 80000) {
                conversationText = conversationText.substring(0, 80000);
            }

            // 构建摘要提示词，要求LLM生成包含关键信息的简洁摘要
            String summaryPrompt = "请总结这段对话以保持上下文连贯性。需要包含：\n" +
                    "1) 已完成的工作内容\n" +
                    "2) 当前状态\n" +
                    "3) 做出的关键决策\n" +
                    "请简洁明了，但保留关键细节。\n\n" + conversationText;

            String summary = completeText(summaryPrompt, 2000);

            // 【步骤3】构建压缩后的新消息列表
            // 包含两条消息：用户消息（告知对话已压缩并提供摘要）+ 助手确认消息
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
     * 计算 token 数量：优先走 Provider（anthropic 原生精确计数 / openai jtokkit 估算），失败时按字符数/4 兜底。
     */
    public int countTokens(List<LlmMessage> messages) {
        try {
            return provider.countTokens(LlmRequest.builder()
                    .model(model)
                    .messages(messages)
                    .build());
        } catch (Exception e) {
            // Provider 不支持时，使用备用估算方法
            System.err.println("[Token 计算失败，使用备用方法] " + e.getMessage());
            try {
                String json = mapper.writeValueAsString(toSessionMessages(messages));
                return json.length() / 4;
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    /**
     * 保存对话记录到磁盘
     * <p>
     * 功能说明：
     * - 在 TRANSCRIPT_DIR 目录下创建以时间戳命名的 JSONL 文件
     * - 每条消息序列化为JSON后写入一行
     * - JSONL格式便于后续读取和分析
     *
     * @param messages 要保存的消息列表
     * @return 保存的文件路径，失败时返回错误标识
     */
    private String saveTranscript(List<LlmMessage> messages) {
        try {
            String sessionId = SessionContextHolder.get();
            String dirName = (sessionId != null && !sessionId.isEmpty()) ? sessionId : "default";
            Path transcriptDir = Paths.get(TRANSCRIPT_DIR, dirName);
            Files.createDirectories(transcriptDir);

            // 使用时间戳生成唯一文件名：transcript_<时间戳>.jsonl
            String timestamp = String.valueOf(System.currentTimeMillis());
            String transcriptPath = transcriptDir.resolve("transcript_" + timestamp + ".jsonl").toString();

            // 使用BufferedWriter逐行写入JSON格式的消息
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(transcriptPath))) {
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("sessionId", dirName);
                meta.put("type", "transcript_meta");
                meta.put("timestamp", timestamp);
                writer.write(mapper.writeValueAsString(meta));
                writer.newLine();
                for (SessionMessage msg : toSessionMessages(messages)) {
                    String json = mapper.writeValueAsString(msg);
                    writer.write(json);
                    writer.newLine();
                }
            }

            log.info("[对话记录已保存: {}]", transcriptPath);
            return transcriptPath;
        } catch (IOException e) {
            log.error("保存对话记录失败", e);
            return "[对话记录保存失败]";
        }
    }

    /**
     * 请求LLM生成对话摘要
     * <p>
     * 功能说明：
     * - 将消息列表序列化为文本（限制80000字符）
     * - 构建专门的摘要提示词
     * - 调用LLM API生成结构化的对话摘要
     *
     * @param messages 要摘要的消息列表
     * @return 生成的摘要文本，失败时返回错误信息
     */
    private String generateSummary(List<LlmMessage> messages) {
        try {
            // 将消息序列化为JSON字符串（作为摘要的输入）
            String conversationText = mapper.writeValueAsString(toSessionMessages(messages));
            if (conversationText.length() > 80000) {
                conversationText = conversationText.substring(0, 80000);
            }

            // 构建中文摘要提示词
            String prompt = "请总结这段对话以保持上下文连贯性。需要包含：\n" +
                    "1) 已完成的工作内容\n" +
                    "2) 当前状态\n" +
                    "3) 做出的关键决策\n" +
                    "请简洁明了，但保留关键细节。\n\n" + conversationText;

            // 无工具的纯文本补全，限制摘要长度为2000 tokens
            return completeText(prompt, 2000);
        } catch (Exception e) {
            log.error("生成摘要失败", e);
            return "[摘要生成失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 无工具的纯文本补全，返回 assistant 文本（失败抛异常由调用方处理）
     */
    private String completeText(String prompt, int maxTokens) {
        LlmResponse response = provider.complete(LlmRequest.builder()
                .model(model)
                .messages(List.of(LlmMessage.user(prompt)))
                .maxTokens(maxTokens)
                .build());
        String text = response.getMessage() == null ? null : response.getMessage().getText();
        return text == null ? "" : text;
    }

    /**
     * 工具结果信息记录
     * <p>
     * 用于在微压缩过程中跟踪工具结果的位置信息：
     * - msgIdx: 消息在消息列表中的索引
     * - partIdx: 工具结果在消息内容块中的索引
     * - result: 工具执行结果块
     * <p>
     * 通过这些信息，可以精确定位和替换需要压缩的工具结果
     */
    private record ToolResultInfo(int msgIdx, int partIdx, SessionBlock result) {
    }
}
