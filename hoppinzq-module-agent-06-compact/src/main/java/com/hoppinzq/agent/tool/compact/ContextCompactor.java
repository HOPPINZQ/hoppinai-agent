package com.hoppinzq.agent.tool.compact;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.session.SessionManager;
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
import java.util.stream.Collectors;

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
 * @author hoppinzq
 */
@Slf4j
public class ContextCompactor {
    private final AnthropicClient client;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SessionManager sessionManager;

    public ContextCompactor(AnthropicClient client, String model, SessionManager sessionManager) {
        this.client = client;
        this.model = model;
        this.sessionManager = sessionManager;
    }

    /**
     * 兼容旧版本构造函数（不使用 SessionManager）
     */
    public ContextCompactor(AnthropicClient client, String model) {
        this(client, model, null);
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
     * 执行时机：
     * - 每次LLM调用前检查消息数
     * - 当消息数 > MAX_MESSAGES 时触发
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<MessageParam> snipCompact(List<MessageParam> messages) {
        if (messages.size() <= MAX_MESSAGES) {
            return messages;
        }

        // 计算保留的头部和尾部
        int headEnd = KEEP_HEAD;
        int tailStart = messages.size() - (MAX_MESSAGES - KEEP_HEAD);

        // 边界条件1：不能把 assistant(tool_use) 和后面的 user(tool_result) 拆开
        if (headEnd > 0 && _messageHasToolUse(messages.get(headEnd - 1))) {
            while (headEnd < messages.size() && _isToolResultMessage(messages.get(headEnd))) {
                headEnd++;
            }
        }

        // 边界条件2：尾部起点也不能拆开 tool_use 和 tool_result
        if (tailStart > 0 && tailStart < messages.size()
                && _isToolResultMessage(messages.get(tailStart))
                && _messageHasToolUse(messages.get(tailStart - 1))) {
            tailStart--;
        }

        // 如果边界处理后头尾重叠，返回原消息列表
        if (headEnd >= tailStart) {
            return messages;
        }

        int snipped = tailStart - headEnd;

        // 构建压缩后的消息列表：头部 + 占位符 + 尾部
        List<MessageParam> newMessages = new ArrayList<>(messages.subList(0, headEnd));
        newMessages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("[已省略 " + snipped + " 条对话历史消息]")
                .build());
        newMessages.addAll(messages.subList(tailStart, messages.size()));

        System.out.printf("\u001b[95m[snip compact]\u001b[0m 裁掉 %d 条消息（保留头部 %d 条）%n",
                snipped, KEEP_HEAD);

        return newMessages;
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
     * 执行时机：
     * - 每次 tool_result 追加后检查
     * - 在 snip_compact 和 micro_compact 之后执行
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<MessageParam> toolResultBudget(List<MessageParam> messages) {
        if (messages.isEmpty()) {
            return messages;
        }

        // 获取最后一条消息
        MessageParam last = messages.get(messages.size() - 1);

        // 检查是否是用户消息且包含 tool_result
        if (!_isToolResultMessage(last)) {
            return messages;
        }

        // 收集所有 tool_result 块
        List<ToolResultInfo> toolResults = new ArrayList<>();
        if (last.content().isBlockParams()) {
            List<ContentBlockParam> contents = last.content().asBlockParams();
            for (int i = 0; i < contents.size(); i++) {
                ContentBlockParam block = contents.get(i);
                if (block.isToolResult()) {
                    toolResults.add(new ToolResultInfo(messages.size() - 1, i, block.toolResult().get()));
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
        List<MessageParam> newMessages = new ArrayList<>(messages);
        MessageParam newLast = last;

        // 需要重建消息
        boolean needRebuild = true;
        List<ContentBlockParam> newBlocks = new ArrayList<>();

        // 处理每个 tool_result
        long currentTotal = totalBytes;
        for (ToolResultInfo info : toolResults) {
            if (currentTotal <= MAX_BYTES_PER_MESSAGE) {
                // 已经降到阈值以下，直接保留原始块
                if (last.content().isBlockParams()) {
                    newBlocks.add(last.content().asBlockParams().get(info.partIdx()));
                }
                break;
            }

            ToolResultBlockParam result = info.result();
            long contentLength = _getContentLength(result);

            if (contentLength <= PERSIST_THRESHOLD) {
                // 小于持久化阈值，保留原始内容
                if (last.content().isBlockParams()) {
                    newBlocks.add(last.content().asBlockParams().get(info.partIdx()));
                }
                continue;
            }

            // 持久化到磁盘
            String originalContent = _extractContentString(result);
            String persistedContent = _persistLargeOutput(result.toolUseId(), originalContent);

            // 创建替换的占位符块
            ToolResultBlockParam newResult = ToolResultBlockParam.builder()
                    .toolUseId(result.toolUseId())
                    .content(persistedContent)
                    .isError(false)
                    .build();

            newBlocks.add(ContentBlockParam.ofToolResult(newResult));

            // 更新总字节数
            currentTotal = currentTotal - contentLength + persistedContent.length();
        }

        // 重建最后一条消息
        if (needRebuild && !newBlocks.isEmpty()) {
            MessageParam.Content newContent = MessageParam.Content.ofBlockParams(newBlocks);
            newMessages.set(newMessages.size() - 1, MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(newContent)
                    .build());
        }

        System.out.printf("\u001b[95m[budget compact]\u001b[0m 持久化 %d 个大工具结果%n",
                toolResults.size());

        return newMessages;
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
     * 执行时机：
     * - API返回prompt_too_long错误时
     * - 作为最后的紧急手段
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<MessageParam> reactiveCompact(List<MessageParam> messages) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        try {
            // 步骤1：保存完整对话记录
            String transcriptPath = _saveTranscriptToDisk(messages);

            // 步骤2：生成摘要
            String summary = _generateSummary(messages);

            // 步骤3：构建压缩后的消息列表
            List<MessageParam> newMessages = new ArrayList<>();

            // 保留首条用户消息（如果存在）
            if (!messages.isEmpty()) {
                MessageParam first = messages.get(0);
                String roleStr = first._role().toString();
                if (roleStr.contains("user") || roleStr.contains("USER")) {
                    newMessages.add(first);
                }
            }

            // 添加摘要消息
            newMessages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content("[紧急压缩以释放上下文]\n\n完整记录: " + transcriptPath + "\n\n" + summary)
                    .build());

            // 保留最近 KEEP_RECENT 条消息
            int tailStart = Math.max(0, messages.size() - KEEP_RECENT);

            // 边界条件：不能拆开 tool_use 和 tool_result
            if (tailStart > 0 && tailStart < messages.size()
                    && _isToolResultMessage(messages.get(tailStart))
                    && _messageHasToolUse(messages.get(tailStart - 1))) {
                tailStart--;
            }

            newMessages.addAll(messages.subList(tailStart, messages.size()));

            System.out.printf("\u001b[95m[reactive compact]\u001b[0m 保留首条 + 最近 %d 条，省略中间 %d 条%n",
                    KEEP_RECENT, messages.size() - newMessages.size());

            return newMessages;

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
    private boolean _messageHasToolUse(MessageParam msg) {
        String roleStr = msg._role().toString();
        if (!roleStr.contains("assistant") && !roleStr.contains("ASSISTANT")) {
            return false;
        }

        if (!msg.content().isBlockParams()) {
            return false;
        }

        for (ContentBlockParam block : msg.content().asBlockParams()) {
            if (block.isToolUse()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断消息是否包含 tool_result
     */
    private boolean _isToolResultMessage(MessageParam msg) {
        String roleStr = msg._role().toString();
        if (!roleStr.contains("user") && !roleStr.contains("USER")) {
            return false;
        }

        if (!msg.content().isBlockParams()) {
            return false;
        }

        for (ContentBlockParam block : msg.content().asBlockParams()) {
            if (block.isToolResult()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取 tool_result 的内容长度
     */
    private long _getContentLength(ToolResultBlockParam result) {
        return _extractContentString(result).length();
    }

    /**
     * 从 ToolResultBlockParam 中提取内容字符串
     */
    private String _extractContentString(ToolResultBlockParam result) {
        // content() 返回 Optional<ToolResultBlockParam.Content>
        // 直接使用 toString() 获取内容字符串
        if (result.content().isPresent()) {
            Object content = result.content().get();
            // 尝试转换为字符串
            if (content instanceof String) {
                return (String) content;
            }
            // 其他类型，转换为字符串
            return content.toString();
        }
        return "";
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
    private String _saveTranscriptToDisk(List<MessageParam> messages) throws IOException {
        Path transcriptDir = Paths.get(ROOT, ".transcripts");
        Files.createDirectories(transcriptDir);

        String timestamp = String.valueOf(System.currentTimeMillis());
        Path filePath = transcriptDir.resolve("transcript_" + timestamp + ".jsonl");

        List<String> lines = new ArrayList<>();
        for (MessageParam msg : messages) {
            lines.add(mapper.writeValueAsString(msg));
        }
        Files.write(filePath, lines);

        return filePath.toString();
    }

    /**
     * 生成对话摘要
     */
    private String _generateSummary(List<MessageParam> messages) {
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

            Message response = client.messages().create(MessageCreateParams.builder()
                    .model(model)
                    .messages(List.of(MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(prompt)
                            .build()))
                    .maxTokens(2000)
                    .build());

            return response.content().stream()
                    .filter(c -> c.isText())
                    .map(c -> c.text().map(TextBlock::text).orElse(""))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "[摘要生成失败: " + e.getMessage() + "]";
        }
    }

    // ============================== 原有方法 ==============================

    /**
     * L2: micro_compact — 旧 tool_result 替换为占位符（保留最近3条）
     *
     * 功能说明：
     * - 遍历所有消息，收集工具调用和工具结果的位置信息
     * - 将旧的工具执行结果替换为简洁的占位符（如"[已执行: read_file]"）
     * - 保留最近KEEP_RECENT个工具结果不变，确保最近的工作上下文完整
     *
     * 执行时机：
     * - 每次调用LLM API前自动执行
     * - 在 snip_compact 和 tool_result_budget 之后执行
     * - 静默执行，对用户透明
     * - 高频执行以控制上下文大小
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    public List<MessageParam> microCompact(List<MessageParam> messages) {
        // 【阶段1：信息收集】
        // 收集所有工具结果的位置信息（消息索引、内容块索引、结果数据）
        List<ToolResultInfo> toolResults = new ArrayList<>();

        // 建立工具ID到工具名称的映射，用于后续生成占位符
        Map<String, String> toolNameMap = new HashMap<>();

        // 遍历所有消息，构建工具结果索引和工具名称映射
        for (int i = 0; i < messages.size(); i++) {
            MessageParam msg = messages.get(i);
            try {
                // 兼容性处理：直接使用 _role() 获取原始值，避免 BigModel API 兼容性问题
                String roleStr = msg._role().toString();
                boolean isAssistant = roleStr.contains("assistant") || roleStr.contains("ASSISTANT");
                boolean isUser = roleStr.contains("user") || roleStr.contains("USER");
                MessageParam.Role role = isUser ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT;

                if (isAssistant) {
                    // 处理助手消息：收集所有工具调用的ID和名称映射
                    if (msg.content().isBlockParams()) {
                        for (ContentBlockParam block : msg.content().asBlockParams()) {
                            if (block.isToolUse()) {
                                ToolUseBlockParam toolUse = block.toolUse().get();
                                toolNameMap.put(toolUse.id(), toolUse.name());
                            }
                        }
                    }
                } else if (isUser) {
                    // 处理用户消息：收集所有工具结果的位置信息
                    if (msg.content().isBlockParams()) {
                        List<ContentBlockParam> contents = msg.content().asBlockParams();
                        for (int j = 0; j < contents.size(); j++) {
                            ContentBlockParam block = contents.get(j);
                            if (block.isToolResult()) {
                                toolResults.add(new ToolResultInfo(i, j, block.toolResult().get()));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 处理消息时出错，打印错误信息并跳过该消息继续处理
                System.err.println("[微压缩] 处理第 " + i + " 条消息时出错: " + e.getMessage());
                e.printStackTrace();
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
        List<MessageParam> newMessages = new ArrayList<>();
        int totalResults = toolResults.size();
        // 计算压缩阈值索引：在此索引之前的工具结果都将被替换为占位符
        int thresholdIndex = totalResults - KEEP_RECENT;

        // 当前遍历到的工具结果索引（从0开始递增）
        int currentResultIndex = 0;

        // 【阶段4：重建消息列表】
        for (MessageParam msg : messages) {
            // 直接使用 _role() 获取原始值，避免 BigModel API 兼容性问题
            String roleStr = msg._role().toString();
            boolean isUser = roleStr.contains("user") || roleStr.contains("USER");
            MessageParam.Role role = isUser ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT;

            if (isUser) {
                if (!msg.content().isBlockParams()) {
                    newMessages.add(msg);
                    continue;
                }
                List<ContentBlockParam> oldBlocks = msg.content().asBlockParams();
                List<ContentBlockParam> newBlocks = new ArrayList<>();
                boolean changed = false;

                for (ContentBlockParam block : oldBlocks) {
                    if (block.isToolResult()) {
                        if (currentResultIndex < thresholdIndex) {
                            // 【压缩处理】将旧的工具执行结果替换为简洁的占位符
                            ToolResultBlockParam tr = block.toolResult().get();
                            String toolId = tr.toolUseId();
                            String toolName = toolNameMap.getOrDefault(toolId, "unknown");

                            // 占位符格式：[已执行: 工具名称]
                            // 这样可以保留工具调用历史，同时大幅减少token占用
                            newBlocks.add(ContentBlockParam.ofToolResult(
                                    ToolResultBlockParam.builder()
                                            .toolUseId(toolId)
                                            .content("[已执行: " + toolName + "]")
                                            .isError(false)
                                            .build()
                            ));
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
                    // 将 List<ContentBlockParam> 转换为 Content 类型
                    if (!newBlocks.isEmpty()) {
                        MessageParam.Content content = MessageParam.Content.ofBlockParams(newBlocks);
                        newMessages.add(MessageParam.builder()
                                .role(role)
                                .content(content)
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

        return newMessages;
    }

    /**
     * Layer 2: 自动压缩 - 完整对话压缩
     *
     * 功能说明：
     * - 将完整对话历史保存到 session 的 transcripts 或磁盘
     * - 请求LLM生成对话摘要（包含已完成的工作、当前状态、关键决策）
     * - 用摘要消息替换原始消息列表，实现上下文压缩
     *
     * 触发条件：
     * - 当估算的token数量超过 TOKEN_THRESHOLD 时自动触发
     *
     * @param messages 原始消息列表
     * @return 压缩后的消息列表（仅包含摘要消息）
     */
    public List<MessageParam> autoCompact(List<MessageParam> messages) {
        return autoCompact(messages, "auto");
    }

    /**
     * Layer 2/3: 完整对话压缩（可指定压缩原因）
     *
     * @param messages 原始消息列表
     * @param reason 压缩原因（auto/manual）
     * @return 压缩后的消息列表
     */
    public List<MessageParam> autoCompact(List<MessageParam> messages, String reason) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        int estimatedTokens = estimateTokens(messages);
        int messageCount = messages.size();

        // 声明 transcriptPath 变量（方法级别）
        String transcriptPath = null;

        // 【步骤1】保存完整对话记录
        // 优先使用 SessionManager，如果不可用则保存到磁盘
        try {
            if (sessionManager != null) {
                // 使用 SessionManager 保存，将在步骤3中添加 transcript 记录
                transcriptPath = "会话 " + sessionManager.getSessionId() + " 的压缩记录";
                System.out.println("[准备压缩] 消息数: " + messageCount + ", 估算tokens: " + estimatedTokens);
            } else {
                // 保存到磁盘的 .transcripts/ 目录
                Path path = Paths.get(ROOT, ".transcripts");
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
                Path filePath = path.resolve("transcript_" + System.currentTimeMillis() + ".jsonl");
                List<String> lines = new ArrayList<>();
                for (MessageParam msg : messages) {
                    lines.add(mapper.writeValueAsString(msg));
                }
                Files.write(filePath, lines);
                transcriptPath = filePath.toString();
                System.out.println("[对话记录已保存: " + transcriptPath + "]");
            }

            // 【步骤2】生成对话摘要
            // 将消息列表转换为文本（限制在80000字符以内以避免超出API限制）
            String conversationText = messages.toString();
            if (conversationText.length() > 80000) {
                conversationText = conversationText.substring(0, 80000);
            }

            // 构建摘要提示词，要求LLM生成包含关键信息的简洁摘要
            String summaryPrompt = "请总结这段对话以保持上下文连贯性。需要包含：\n" +
                    "1) 已完成的工作内容\n" +
                    "2) 当前状态\n" +
                    "3) 做出的关键决策\n" +
                    "请简洁明了，但保留关键细节。\n\n" + conversationText;

            Message summaryMsg = client.messages().create(MessageCreateParams.builder()
                    .model(MODEL)
                    .messages(List.of(MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(summaryPrompt)
                            .build()))
                    .maxTokens(2000)
                    .build());

            String summary = summaryMsg.content().stream()
                    .filter(ContentBlock::isText)
                    .map(cb -> cb.text().get().text())
                    .collect(Collectors.joining());

            // 【步骤3】将压缩记录保存到 SessionManager（如果可用）
            if (sessionManager != null) {
                try {
                    // 创建 transcript 记录
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
            // 包含两条消息：用户消息（告知对话已压缩并提供摘要）+ 助手确认消息
            List<MessageParam> newHistory = new ArrayList<>();
            newHistory.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content("[对话已压缩。完整记录: " + transcriptPath + "]\n\n" + summary)
                    .build());
            newHistory.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .content("收到。我已从摘要中获取了上下文。继续工作。")
                    .build());

            return newHistory;

        } catch (IOException e) {
            // 压缩失败时返回原始消息列表，确保对话不中断
            System.err.println("自动压缩时出错: " + e.getMessage());
            return messages;
        }
    }

    /**
     * 保存对话记录到磁盘
     *
     * 功能说明：
     * - 在 .transcripts 目录下创建以时间戳命名的 JSONL 文件
     * - 每条消息序列化为JSON后写入一行
     * - JSONL格式便于后续读取和分析
     *
     * 注意：虽然 SessionManager 已经存储会话数据，但这里保留磁盘备份
     * 因为 SessionManager 存储的是压缩后的消息，压缩前的完整对话需要额外保存
     *
     * @param messages 要保存的消息列表
     * @return 保存的文件路径，失败时返回错误标识
     */
    private String saveTranscript(List<MessageParam> messages) {
        try {
            // 创建transcript目录（如果不存在）
            Path transcriptDir = Paths.get(ROOT, ".transcripts");
            Files.createDirectories(transcriptDir);

            // 使用时间戳生成唯一文件名：transcript_<时间戳>.jsonl
            String timestamp = String.valueOf(System.currentTimeMillis());
            String transcriptPath = transcriptDir.resolve("transcript_" + timestamp + ".jsonl").toString();

            // 使用BufferedWriter逐行写入JSON格式的消息
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(transcriptPath))) {
                for (MessageParam msg : messages) {
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
     *
     * 功能说明：
     * - 将消息列表序列化为文本（限制80000字符）
     * - 构建专门的摘要提示词
     * - 调用LLM API生成结构化的对话摘要
     *
     * @param messages 要摘要的消息列表
     * @return 生成的摘要文本，失败时返回错误信息
     */
    private String generateSummary(List<MessageParam> messages) {
        try {
            // 将消息序列化为JSON字符串（作为摘要的输入）
            String conversationText = mapper.writeValueAsString(messages);
            if (conversationText.length() > 80000) {
                conversationText = conversationText.substring(0, 80000);
            }

            // 构建中文摘要提示词
            String prompt = "请总结这段对话以保持上下文连贯性。需要包含：\n" +
                "1) 已完成的工作内容\n" +
                "2) 当前状态\n" +
                "3) 做出的关键决策\n" +
                "请简洁明了，但保留关键细节。\n\n" + conversationText;
            
            // 构建API请求参数
            MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .messages(List.of(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(prompt)
                    .build()))
                .maxTokens(2000)  // 限制摘要长度为2000 tokens
                .build();

            // 调用LLM生成摘要
            Message response = client.messages().create(params);
            // 提取并拼接所有文本块
            return response.content().stream()
                .filter(c -> c.isText())
                .map(c -> c.text().map(TextBlock::text).orElse(""))
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("生成摘要失败", e);
            return "[摘要生成失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 估算消息列表的token数量
     *
     * 说明：
     * - 使用粗略估算方法：约4个字符 ≈ 1个token
     * - 将消息列表序列化为JSON后计算长度
     * - 用于判断是否需要触发自动压缩
     *
     * 注意：
     * - 这是简化估算，实际token数量可能有所不同
     * - 对于精确控制，应使用官方的tokenizer工具
     *
     * @param messages 要估算的消息列表
     * @return 估算的token数量
     */
    public static int estimateTokens(List<MessageParam> messages) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(messages);
            return json.length() / 4;
        } catch (Exception e) {
            // 序列化失败时返回0，表示无法估算
            return 0;
        }
    }

    /**
     * 工具结果信息记录
     *
     * 用于在微压缩过程中跟踪工具结果的位置信息：
     * - msgIdx: 消息在消息列表中的索引
     * - partIdx: 工具结果在消息内容块中的索引
     * - result: 工具执行结果的完整数据
     *
     * 通过这些信息，可以精确定位和替换需要压缩的工具结果
     */
    private static record ToolResultInfo(int msgIdx, int partIdx, ToolResultBlockParam result) {}
}
