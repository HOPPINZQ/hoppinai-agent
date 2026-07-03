package com.hoppinzq.agent.session;

import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Usage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 会话编排器：维护当前 {@code sessionId} 与内存中的消息副本，
 * 在每条消息追加时自动持久化到 {@link SessionStore}。
 * <p>与 agent 解耦：agent 只需在添加消息时调用 {@link #onMessageAppended(MessageParam)}，
 * 启动时调用 {@link #populate(List)} 即可恢复历史。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   SessionManager sm = new SessionManager();
 *   if (args.length > 0 && sm.resume(args[0])) {
 *       System.out.println("已恢复会话 " + sm.getSessionId());
 *   } else {
 *       sm.startNew();
 *       System.out.println("新会话 " + sm.getSessionId());
 *   }
 *   agent.setSessionManager(sm);
 *   sm.populate(agent.getMessageParams());   // 由 ZQAgent.run 启动时自动调用
 *   agent.run();
 * }</pre>
 *
 * @author hoppinzq
 */
public class SessionManager {

    private static final DateTimeFormatter ID_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SessionStore store;
    private final MessageConverter converter;
    private SessionData sessionData = SessionData.builder().build();
    private String sessionId;

    public SessionManager() {
        this(new SessionStore(), new MessageConverter());
    }

    public SessionManager(SessionStore store, MessageConverter converter) {
        this.store = store;
        this.converter = converter;
    }

    // ============================== 生命周期 ==============================

    /**
     * 创建一个新会话，生成形如 {@code yyyyMMdd-HHmmss-XXXX} 的 ID。
     */
    public String startNew() {
        this.sessionId = generateId();
        this.sessionData = SessionData.builder()
                .messages(new ArrayList<>())
                .usage(new ArrayList<>())
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
        // 预先落盘一份空文件，便于 listIds 时就能看到
        persist();
        return this.sessionId;
    }

    /**
     * 恢复指定会话。
     *
     * @return 若会话存在且非空返回 {@code true}；否则返回 {@code false}（仍会以该 id 继续会话）
     */
    public boolean resume(String sessionId) {
        this.sessionId = sessionId;
        this.sessionData = store.load(sessionId);

        // 验证会话数据完整性：检查是否有未完成的 tool_use
        if (!this.sessionData.getMessages().isEmpty()) {
            boolean hasIncompleteToolUse = validateMessageIntegrity();
            if (hasIncompleteToolUse) {
                System.out.printf("\u001b[93m警告: 会话 %s 数据不完整，包含未完成的 tool_use，已清理\u001b[0m%n", sessionId);
                // 清理会话数据
                this.sessionData = SessionData.builder()
                        .messages(new ArrayList<>())
                        .usage(new ArrayList<>())
                        .createdAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .build();
                // 清空磁盘上的会话文件
                try {
                    store.save(sessionId, this.sessionData);
                } catch (Exception e) {
                    // 忽略保存失败
                }
                return false;
            }
        }

        boolean hasMessages = !this.sessionData.getMessages().isEmpty();
        // 若有历史 usage 数据，打印统计摘要
        if (hasMessages && !sessionData.getUsage().isEmpty()) {
            printSummary();
        }
        return hasMessages;
    }

    /**
     * 验证消息序列的完整性，检查是否有未完成的 tool_use。
     *
     * @return 如果发现未完成的 tool_use 返回 true，否则返回 false
     */
    private boolean validateMessageIntegrity() {
        Set<String> completedToolUses = new HashSet<>();
        Set<String> pendingToolUses = new HashSet<>();

        for (SessionMessage sm : sessionData.getMessages()) {
            if (sm.getBlocks() == null) {
                continue;
            }
            for (SessionBlock block : sm.getBlocks()) {
                if ("tool_use".equals(block.getType())) {
                    String toolUseId = block.getToolUseId();
                    if (toolUseId != null) {
                        pendingToolUses.add(toolUseId);
                    }
                } else if ("tool_result".equals(block.getType())) {
                    String toolUseId = block.getToolUseId();
                    if (toolUseId != null) {
                        pendingToolUses.remove(toolUseId);
                        completedToolUses.add(toolUseId);
                    }
                }
            }
        }

        // 如果还有未完成的 tool_use，说明数据不完整
        return !pendingToolUses.isEmpty();
    }

    /**
     * 列出所有已存在的会话 ID。
     */
    public List<String> listSessions() {
        return store.listIds();
    }

    // ============================== 与 agent 的桥接 ==============================

    /**
     * 把历史消息回放到 agent 的 messageParams。
     * <p>由 agent 在 run 启动时调用，恢复上下文。
     */
    public void populate(List<MessageParam> out) {
        for (SessionMessage sm : sessionData.getMessages()) {
            out.add(converter.toMessageParam(sm));
        }
    }

    /**
     * 每当 agent 追加一条新消息时调用：转成 {@link SessionMessage} 并落盘。
     */
    public void onMessageAppended(MessageParam param) {
        sessionData.getMessages().add(converter.toSessionMessage(param));
        sessionData.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        persist();
    }

    /**
     * 每次调用 LLM 后记录 token 使用情况。
     */
    public void recordUsage(Usage usage) {
        if (usage == null) {
            return;
        }
        TokenUsage tokenUsage = TokenUsage.builder()
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .cacheReadTokens(usage.cacheReadInputTokens().orElse(null))
                .cacheCreationTokens(usage.cacheCreationInputTokens().orElse(null))
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
        sessionData.getUsage().add(tokenUsage);
        persist();
    }

    /**
     * 记录压缩操作。
     *
     * @param record 压缩记录
     */
    public void recordTranscript(TranscriptRecord record) {
        if (record == null) {
            return;
        }
        if (sessionData.getTranscripts() == null) {
            sessionData.setTranscripts(new ArrayList<>());
        }
        sessionData.getTranscripts().add(record);
        sessionData.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        persist();
    }

    /**
     * 当前消息数量。
     */
    public int historySize() {
        return sessionData.getMessages().size();
    }

    public String getSessionId() {
        return sessionId;
    }

    // ============================== 内部 ==============================

    private void persist() {
        if (sessionId == null) {
            return;
        }
        store.save(sessionId, sessionData);
    }

    // ============================== Token 统计 ==============================

    /**
     * 本次会话的总 token 消耗（input + output）。
     */
    public long getTotalTokens() {
        return sessionData.getUsage().stream()
                .mapToLong(TokenUsage::getTotalTokens)
                .sum();
    }

    /**
     * 本次会话的输入 token 总和。
     */
    public long getInputTokens() {
        return sessionData.getUsage().stream()
                .mapToLong(u -> u.getInputTokens() != null ? u.getInputTokens() : 0)
                .sum();
    }

    /**
     * 本次会话的输出 token 总和。
     */
    public long getOutputTokens() {
        return sessionData.getUsage().stream()
                .mapToLong(u -> u.getOutputTokens() != null ? u.getOutputTokens() : 0)
                .sum();
    }

    /**
     * 本次会话的缓存命中 token 总和。
     */
    public long getCacheReadTokens() {
        return sessionData.getUsage().stream()
                .mapToLong(u -> u.getCacheReadTokens() != null ? u.getCacheReadTokens() : 0)
                .sum();
    }

    /**
     * 本次会话的缓存创建 token 总和。
     */
    public long getCacheCreationTokens() {
        return sessionData.getUsage().stream()
                .mapToLong(u -> u.getCacheCreationTokens() != null ? u.getCacheCreationTokens() : 0)
                .sum();
    }

    /**
     * 本次会话的缓存命中率（0-1）。
     * <p>注：Anthropic API 的 {@code input_tokens} 与 {@code cache_read_input_tokens}
     * 是独立统计，后者是额外从 prompt cache 读取的 token 数。
     */
    public double getCacheHitRate() {
        long cached = getCacheReadTokens();
        long input = getInputTokens();
        long totalInput = cached + input;
        if (totalInput == 0) {
            return 0.0;
        }
        return (double) cached / totalInput;
    }

    /**
     * 本次会话的 LLM 调用次数。
     */
    public int getCallCount() {
        return sessionData.getUsage().size();
    }

    /**
     * 获取本次会话的 token 使用明细（只读）。
     */
    public List<TokenUsage> getUsageList() {
        return new ArrayList<>(sessionData.getUsage());
    }

    /**
     * 获取本次会话的压缩历史记录（只读）。
     */
    public List<TranscriptRecord> getTranscripts() {
        if (sessionData.getTranscripts() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(sessionData.getTranscripts());
    }

    /**
     * 打印本次会话的 token 统计摘要。
     */
    public void printSummary() {
        System.out.printf("\u001b[90m========== 会话 %s 的 token 统计 ==========\u001b[0m%n", sessionId);
        System.out.printf("\u001b[90m调用次数\u001b[0m: %d%n", getCallCount());
        System.out.printf("\u001b[90m输入 tokens\u001b[0m: %,d%n", getInputTokens());
        System.out.printf("\u001b[90m输出 tokens\u001b[0m: %,d%n", getOutputTokens());
        System.out.printf("\u001b[90m缓存命中 tokens\u001b[0m: %,d%n", getCacheReadTokens());
        System.out.printf("\u001b[90m缓存创建 tokens\u001b[0m: %,d%n", getCacheCreationTokens());
        System.out.printf("\u001b[90m总计 tokens\u001b[0m: %,d%n", getTotalTokens());
        if (getInputTokens() > 0) {
            System.out.printf("\u001b[90m缓存命中率\u001b[0m: %.1f%%%n", getCacheHitRate() * 100);
        }
        System.out.println();
    }

    private String generateId() {
        String time = LocalDateTime.now().format(ID_FMT);
        String rand = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000));
        return time + "-" + rand;
    }
}
