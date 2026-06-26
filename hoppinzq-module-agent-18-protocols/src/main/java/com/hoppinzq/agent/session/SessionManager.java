package com.hoppinzq.agent.session;

import com.anthropic.models.messages.MessageParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    private final List<SessionMessage> messages = new ArrayList<>();
    private String sessionId;

    public SessionManager() {
        this(new SessionStore(), new MessageConverter());
    }

    public SessionManager(SessionStore store, MessageConverter converter) {
        this.store = store;
        this.converter = converter;
    }

    // ============================== 生命周期 ==============================

    /** 创建一个新会话，生成形如 {@code yyyyMMdd-HHmmss-XXXX} 的 ID。 */
    public String startNew() {
        this.sessionId = generateId();
        this.messages.clear();
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
        this.messages.clear();
        this.messages.addAll(store.load(sessionId));
        return !this.messages.isEmpty();
    }

    /** 列出所有已存在的会话 ID。 */
    public List<String> listSessions() {
        return store.listIds();
    }

    // ============================== 与 agent 的桥接 ==============================

    /**
     * 把历史消息回放到 agent 的 messageParams。
     * <p>由 agent 在 run 启动时调用，恢复上下文。
     */
    public void populate(List<MessageParam> out) {
        for (SessionMessage sm : messages) {
            out.add(converter.toMessageParam(sm));
        }
    }

    /**
     * 每当 agent 追加一条新消息时调用：转成 {@link SessionMessage} 并落盘。
     */
    public void onMessageAppended(MessageParam param) {
        messages.add(converter.toSessionMessage(param));
        persist();
    }

    /** 当前消息数量。 */
    public int historySize() {
        return messages.size();
    }

    public String getSessionId() {
        return sessionId;
    }

    // ============================== 内部 ==============================

    private void persist() {
        if (sessionId == null) {
            return;
        }
        store.save(sessionId, messages);
    }

    private String generateId() {
        String time = LocalDateTime.now().format(ID_FMT);
        String rand = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000));
        return time + "-" + rand;
    }
}
