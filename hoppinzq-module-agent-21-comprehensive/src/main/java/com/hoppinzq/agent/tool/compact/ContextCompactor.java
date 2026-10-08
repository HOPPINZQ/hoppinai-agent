package com.hoppinzq.agent.tool.compact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.session.MessageConverter;
import com.hoppinzq.agent.session.SessionBlock;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.session.SessionMessage;
import com.hoppinzq.agent.session.TranscriptRecord;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 上下文压缩器 - 四层压缩策略
 *
 * 压缩流程（参考Python s08设计）：
 * - L1: snip_compact      — 裁掉中间消息（消息数 > 50）
 * - L2: micro_compact     — 旧tool_result替换为占位符（保留最近3条）
 * - L3: tool_result_budget — 持久化大输出到磁盘（单次消息 > 30KB）
 * - L4: auto_compact      — LLM完整摘要（token超阈值）
 * - Emergency: reactive_compact — API返回prompt_too_long时触发
 *
 * 核心原则：cheap first, expensive last
 * 执行顺序：budget → snip → micro → auto
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
    private final SessionManager sessionManager;
    private final MessageConverter converter = new MessageConverter();

    public ContextCompactor(LlmProvider provider, String model, SessionManager sessionManager) {
        this.provider = provider;
        this.model = model;
        this.sessionManager = sessionManager;
    }

    /**
     * 兼容旧版本构造函数（不使用 SessionManager）
     */
    public ContextCompactor(LlmProvider provider, String model) {
        this(provider, model, null);
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

    // ============================== L1: snip_compact ==============================
    /**
     * L1: snip_compact — 裁掉中间消息（消息数超过50条时）
     *
     * 功能说明：
     * - 保留头部 KEEP_HEAD 条消息（初始上下文）
     * - 保留尾部消息（当前工作）
     * - 中间全部裁掉，替换为占位符
     * - 特殊处理：不能把 assistant(tool_use) 和后面的 user(tool_result) 拆开
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<LlmMessage> snipCompact(List<LlmMessage> messages) {
        if (messages.size() <= MAX_MESSAGES) {
            return messages;
        }
        List<SessionMessage> sms = toSessionMessages(messages);

        // 计算保留的头部和尾部
        int headEnd = KEEP_HEAD;
        int tailStart = sms.size() - (MAX_MESSAGES - KEEP_HEAD);

        // 边界条件1：不能把 assistant(tool_use) 和后面的 user(tool_result) 拆开
        if (headEnd > 0 && _messageHasToolUse(sms.get(headEnd - 1))) {
            while (headEnd < sms.size() && _isToolResultMessage(sms.get(headEnd))) {
                headEnd++;
            }
        }

        // 边界条件2：尾部起点也不能拆开 tool_use 和 tool_result
        if (tailStart > 0 && tailStart < sms.size()
                && _isToolResultMessage(sms.get(tailStart))
                && _messageHasToolUse(sms.get(tailStart - 1))) {
            tailStart--;
        }

        // 如果边界处理后头尾重叠，返回原消息列表
        if (headEnd >= tailStart) {
            return messages;
        }

        int snipped = tailStart - headEnd;

        // 构建压缩后的消息列表：头部 + 占位符 + 尾部
        List<SessionMessage> newMessages = new ArrayList<>(sms.subList(0, headEnd));
        newMessages.add(SessionMessage.builder()
                .role("user")
                .text("[已省略 " + snipped + " 条对话历史消息]")
                .build());
        newMessages.addAll(sms.subList(tailStart, sms.size()));

        System.out.printf("\u001b[95m[snip compact]\u001b[0m 裁掉 %d 条消息（保留头部 %d 条）%n",
                snipped, KEEP_HEAD);

        return toLlmMessages(newMessages);
    }

    // ============================== L3: tool_result_budget ==============================
    /**
     * L3: tool_result_budget — 持久化大输出到磁盘
     *
     * 功能说明：
     * - 检查最后一条消息中的所有 tool_result
     * - 如果总字节数超过 MAX_BYTES_PER_MESSAGE，则持久化大的结果
     * - 按大小排序，优先持久化最大的结果
     * - 将持久化的结果替换为占位符，包含文件路径和预览
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<LlmMessage> toolResultBudget(List<LlmMessage> messages) {
        if (messages.isEmpty()) {
            return messages;
        }
        List<SessionMessage> sms = toSessionMessages(messages);

        // 获取最后一条消息
        SessionMessage last = sms.get(sms.size() - 1);

        // 检查是否是用户消息且包含 tool_result
        if (!_isToolResultMessage(last)) {
            return messages;
        }

        // 收集所有 tool_result 块
        List<ToolResultInfo> toolResults = new ArrayList<>();
        if (last.getBlocks() != null) {
            List<SessionBlock> contents = last.getBlocks();
            for (int i = 0; i < contents.size(); i++) {
                SessionBlock block = contents.get(i);
                if ("tool_result".equals(block.getType())) {
                    toolResults.add(new ToolResultInfo(sms.size() - 1, i, block));
                }
            }
        }

        // 计算总字节数
        long totalBytes = 0;
        for (ToolResultInfo info : toolResults) {
            totalBytes += _getContentLength(info.result());
        }

        if (totalBytes <= MAX_BYTES_PER_MESSAGE) {
            return messages;
        }

        // 按大小排序（大到小）
        toolResults.sort((a, b) -> Long.compare(_getContentLength(b.result()), _getContentLength(a.result())));

        // 创建新的消息列表（复制）
        List<SessionMessage> newMessages = new ArrayList<>(sms);

        // 重建最后一条消息的块列表
        List<SessionBlock> newBlocks = new ArrayList<>();

        // 处理每个 tool_result
        long currentTotal = totalBytes;
        for (ToolResultInfo info : toolResults) {
            if (currentTotal <= MAX_BYTES_PER_MESSAGE) {
                // 已经降到阈值以下，直接保留原始块
                newBlocks.add(last.getBlocks().get(info.partIdx()));
                break;
            }

            SessionBlock result = info.result();
            long contentLength = _getContentLength(result);

            if (contentLength <= PERSIST_THRESHOLD) {
                // 小于持久化阈值，保留原始内容
                newBlocks.add(last.getBlocks().get(info.partIdx()));
                continue;
            }

            // 持久化到磁盘
            String originalContent = _extractContentString(result);
            String persistedContent = _persistLargeOutput(result.getToolUseId(), originalContent);

            // 创建替换的占位符块
            newBlocks.add(SessionBlock.builder()
                    .type("tool_result")
                    .toolUseId(result.getToolUseId())
                    .toolResultContent(persistedContent)
                    .isError(false)
                    .build());

            // 更新总字节数
            currentTotal = currentTotal - contentLength + persistedContent.length();
        }

        // 重建最后一条消息
        if (!newBlocks.isEmpty()) {
            newMessages.set(newMessages.size() - 1, SessionMessage.builder()
                    .role("user")
                    .blocks(newBlocks)
                    .build());
        }

        System.out.printf("\u001b[95m[budget compact]\u001b[0m 持久化 %d 个大工具结果%n",
                toolResults.size());

        return toLlmMessages(newMessages);
    }

    // ============================== Emergency: reactive_compact ==============================
    /**
     * 紧急压缩 - 当API返回prompt_too_long时触发
     *
     * 功能说明：
     * - 保存完整对话记录到磁盘
     * - 生成对话摘要
     * - 保留首条用户消息 + 最近 KEEP_RECENT 条消息
     * - 中间用摘要替换
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<LlmMessage> reactiveCompact(List<LlmMessage> messages) {
        try {
            List<SessionMessage> sms = toSessionMessages(messages);

            // 步骤1：保存完整对话记录
            String transcriptPath = _saveTranscriptToDisk(sms);

            // 步骤2：生成摘要
            String summary = _generateSummary(sms);

            // 步骤3：构建压缩后的消息列表
            List<SessionMessage> newMessages = new ArrayList<>();

            // 保留首条用户消息（如果存在）
            if (!sms.isEmpty()) {
                SessionMessage first = sms.get(0);
                if ("user".equals(first.getRole())) {
                    newMessages.add(first);
                }
            }

            // 添加摘要消息
            newMessages.add(SessionMessage.builder()
                    .role("user")
                    .text("[紧急压缩以释放上下文]\n\n完整记录: " + transcriptPath + "\n\n" + summary)
                    .build());

            // 保留最近 KEEP_RECENT 条消息
            int tailStart = Math.max(0, sms.size() - KEEP_RECENT);

            // 边界条件：不能拆开 tool_use 和 tool_result
            if (tailStart > 0 && tailStart < sms.size()
                    && _isToolResultMessage(sms.get(tailStart))
                    && _messageHasToolUse(sms.get(tailStart - 1))) {
                tailStart--;
            }

            newMessages.addAll(sms.subList(tailStart, sms.size()));

            System.out.printf("\u001b[95m[reactive compact]\u001b[0m 保留首条 + 最近 %d 条，省略中间 %d 条%n",
                    KEEP_RECENT, sms.size() - newMessages.size());

            return toLlmMessages(newMessages);

        } catch (Exception e) {
            System.err.println("紧急压缩失败: " + e.getMessage());
            // 失败时返回原始消息列表
            return messages;
        }
    }

    // ============================== 辅助方法 ==============================

    /**
     * 判断消息是否包含 tool_use
     */
    private boolean _messageHasToolUse(SessionMessage msg) {
        if (!"assistant".equals(msg.getRole()) || msg.getBlocks() == null) {
            return false;
        }
        for (SessionBlock block : msg.getBlocks()) {
            if ("tool_use".equals(block.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断消息是否包含 tool_result
     */
    private boolean _isToolResultMessage(SessionMessage msg) {
        if (!"user".equals(msg.getRole()) || msg.getBlocks() == null) {
            return false;
        }
        for (SessionBlock block : msg.getBlocks()) {
            if ("tool_result".equals(block.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取 tool_result 的内容长度
     */
    private long _getContentLength(SessionBlock result) {
        return _extractContentString(result).length();
    }

    /**
     * 从 tool_result 块中提取内容字符串
     */
    private String _extractContentString(SessionBlock result) {
        return result.getToolResultContent() == null ? "" : result.getToolResultContent();
    }

    /**
     * 持久化大输出到磁盘
     */
    private String _persistLargeOutput(String toolUseId, String output) {
        try {
            // 创建 tool-results 目录
            Path toolResultsDir = Paths.get(ROOT, ".task_outputs", "tool-results");
            Files.createDirectories(toolResultsDir);

            // 保存到文件
            Path filePath = toolResultsDir.resolve(toolUseId + ".txt");
            if (!Files.exists(filePath)) {
                Files.write(filePath, output.getBytes());
            }

            // 返回占位符
            String preview = output.length() > 2000 ? output.substring(0, 2000) : output;
            return String.format(
                    "<persisted-output>\n完整输出: %s\n预览:\n%s\n</persisted-output>",
                    filePath, preview
            );
        } catch (IOException e) {
            System.err.println("持久化工具结果失败: " + e.getMessage());
            return "[工具结果持久化失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 保存对话记录到磁盘
     */
    private String _saveTranscriptToDisk(List<SessionMessage> messages) throws IOException {
        Path transcriptDir = Paths.get(ROOT, ".transcripts");
        Files.createDirectories(transcriptDir);

        String timestamp = String.valueOf(System.currentTimeMillis());
        Path filePath = transcriptDir.resolve("transcript_" + timestamp + ".jsonl");

        List<String> lines = new ArrayList<>();
        for (SessionMessage msg : messages) {
            lines.add(mapper.writeValueAsString(msg));
        }
        Files.write(filePath, lines);

        return filePath.toString();
    }

    /**
     * 生成对话摘要（1 次 LLM 调用，不带工具）
     */
    private String _generateSummary(List<SessionMessage> messages) {
        try {
            String conversationText = mapper.writeValueAsString(messages);
            if (conversationText.length() > 80000) {
                conversationText = conversationText.substring(0, 80000);
            }

            String prompt = "请总结这段对话以保持上下文连贯性。需要包含：\n" +
                    "1) 已完成的工作内容\n" +
                    "2) 当前状态\n" +
                    "3) 做出的关键决策\n" +
                    "4) 剩余工作\n" +
                    "5) 用户约束\n" +
                    "请简洁明了，但保留关键细节。\n\n" + conversationText;

            return completeText(prompt, 2000);
        } catch (Exception e) {
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

    // ============================== L2: micro_compact ==============================
    /**
     * L2: micro_compact — 旧 tool_result 替换为占位符（保留最近3条）
     *
     * 功能说明：
     * - 遍历所有消息，收集工具调用和工具结果的位置信息
     * - 将旧的工具执行结果替换为简洁的占位符（如"[已执行: read_file]"）
     * - 保留最近KEEP_RECENT个工具结果不变，确保最近的工作上下文完整
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
        // 在此索引之前的工具结果都将被替换为占位符
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
                            // 保留最近的工具结果
                            newBlocks.add(block);
                        }
                        currentResultIndex++;
                    } else {
                        // 非工具结果内容（如文本）直接保留
                        newBlocks.add(block);
                    }
                }

                if (changed && !newBlocks.isEmpty()) {
                    newMessages.add(SessionMessage.builder()
                            .role("user")
                            .blocks(newBlocks)
                            .build());
                } else {
                    // 内容未改变（或防御性兜底），保留原消息
                    newMessages.add(msg);
                }
            } else {
                newMessages.add(msg);
            }
        }

        return toLlmMessages(newMessages);
    }

    /**
     * L4: 自动压缩 - 完整对话压缩
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表（仅包含摘要消息）
     */
    public List<LlmMessage> autoCompact(List<LlmMessage> messages) {
        return autoCompact(messages, "auto");
    }

    /**
     * L4: 完整对话压缩（可指定压缩原因）
     *
     * @param messages 原始消息列表
     * @param reason 压缩原因（auto/manual）
     * @return 压缩后的消息列表
     */
    public List<LlmMessage> autoCompact(List<LlmMessage> messages, String reason) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        int estimatedTokens = countTokens(messages);
        int messageCount = messages.size();

        // 声明 transcriptPath 变量（方法级别）
        String transcriptPath = null;

        // 【步骤1】保存完整对话记录
        try {
            if (sessionManager != null) {
                transcriptPath = "会话 " + sessionManager.getSessionId() + " 的压缩记录";
                System.out.println("[准备压缩] 消息数: " + messageCount + ", 估算tokens: " + estimatedTokens);
            } else {
                Path path = Paths.get(ROOT, ".transcripts");
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
                Path filePath = path.resolve("transcript_" + System.currentTimeMillis() + ".jsonl");
                List<String> lines = new ArrayList<>();
                for (SessionMessage msg : toSessionMessages(messages)) {
                    lines.add(mapper.writeValueAsString(msg));
                }
                Files.write(filePath, lines);
                transcriptPath = filePath.toString();
                System.out.println("[对话记录已保存: " + transcriptPath + "]");
            }

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

            // 【步骤3】将压缩记录保存到 SessionManager（如果可用）
            if (sessionManager != null) {
                try {
                    TranscriptRecord record = TranscriptRecord.builder()
                            .timestamp(timestamp)
                            .messageCount(messageCount)
                            .estimatedTokens(estimatedTokens)
                            .summary(summary)
                            .reason(reason)
                            .build();
                    sessionManager.recordTranscript(record);
                    System.out.println("[压缩记录已保存到会话]");
                } catch (Exception e) {
                    System.err.println("[压缩记录保存失败] " + e.getMessage());
                }
            }

            // 【步骤4】构建压缩后的新消息列表
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
     * 工具结果信息记录
     *
     * 用于在压缩过程中跟踪工具结果的位置信息：
     * - msgIdx: 消息在消息列表中的索引
     * - partIdx: 工具结果在消息内容块中的索引
     * - result: 工具执行结果块
     */
    private static record ToolResultInfo(int msgIdx, int partIdx, SessionBlock result) {}
}
