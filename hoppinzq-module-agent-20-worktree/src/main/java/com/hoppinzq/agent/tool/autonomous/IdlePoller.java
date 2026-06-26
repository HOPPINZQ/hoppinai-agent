package com.hoppinzq.agent.tool.autonomous;

import com.hoppinzq.agent.tool.bus.MailboxMessage;
import com.hoppinzq.agent.tool.bus.MessageBus;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;
import com.hoppinzq.agent.tool.schema.TaskInput;
import com.hoppinzq.agent.tool.task.TaskManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 空闲轮询器。Teammate 在 IDLE 阶段调用 {@link #poll}，每 5 秒检查一次三件事：
 * <ol>
 *   <li>mailbox 是否有 {@code shutdown_request} → 返回 SHUTDOWN</li>
 *   <li>mailbox 是否有普通 {@code message} → 返回 WORK（带消息正文）</li>
 *   <li>{@link TaskManager#scanUnclaimedTasks()} 是否有可认领任务 → 返回 WORK（带任务摘要）</li>
 * </ol>
 *
 * <p>连续 60 秒都没有可做的工作 → 返回 TIMEOUT（teammate 退出）。
 *
 * @author hoppinzq
 */
@Slf4j
public class IdlePoller {

    /** 单轮睡眠间隔（毫秒） */
    public static final long POLL_INTERVAL_MS = 5_000L;
    /** 总轮询上限（毫秒）。超时后 teammate 自行退出。 */
    public static final long POLL_TIMEOUT_MS = 60_000L;

    /**
     * @param teammateName 当前 teammate 的名字
     * @param bus          共享 MessageBus
     * @param registry     协议注册表（暂未使用，保留参数便于扩展）
     * @param taskManager  任务管理器
     * @return 一个 {@link Outcome}：WORK / SHUTDOWN / TIMEOUT 之一
     */
    public Outcome poll(String teammateName,
                        MessageBus bus,
                        ProtocolRegistry registry,
                        TaskManager taskManager) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            // 1. 先看 mailbox
            List<MailboxMessage> inbox = bus.readInbox(teammateName);
            for (MailboxMessage msg : inbox) {
                if ("shutdown_request".equals(msg.getType())) {
                    // content 形如 "<requestId>|<reason>"
                    String requestId = msg.getContent();
                    int bar = requestId.indexOf('|');
                    if (bar >= 0) {
                        requestId = requestId.substring(0, bar);
                    }
                    log.info("[{}] 收到 shutdown_request, requestId={}", teammateName, requestId);
                    return Outcome.shutdown(requestId);
                }
            }
            // 2. 普通消息：拼接正文，当作用户输入投递给 teammate 的对话
            StringBuilder msgBuf = new StringBuilder();
            for (MailboxMessage msg : inbox) {
                if ("message".equals(msg.getType())) {
                    if (msgBuf.length() > 0) msgBuf.append("\n");
                    msgBuf.append("[from ").append(msg.getFrom()).append("] ")
                            .append(msg.getContent());
                }
            }
            if (msgBuf.length() > 0) {
                return Outcome.work(msgBuf.toString());
            }
            // 3. 看任务板
            List<TaskInput> ready = taskManager.scanUnclaimedTasks();
            if (!ready.isEmpty()) {
                TaskInput first = ready.get(0);
                return Outcome.work("[autonomous] new task available: " + first.getDisplayString());
            }
            // 4. 本轮无事可做，睡 5 秒后重试
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                // 被打断当作 continue：清掉打断标志，进入下一轮
                Thread.currentThread().interrupt();
            }
        }
        return Outcome.timeout();
    }

    /**
     * 轮询结果。type 决定 teammate 接下来怎么处理 payload。
     */
    public static class Outcome {
        public enum Type { WORK, SHUTDOWN, TIMEOUT }

        private final Type type;
        private final String payload;

        private Outcome(Type type, String payload) {
            this.type = type;
            this.payload = payload;
        }

        public Type getType() { return type; }
        public String getPayload() { return payload; }

        public static Outcome work(String prompt) { return new Outcome(Type.WORK, prompt); }
        public static Outcome shutdown(String requestId) { return new Outcome(Type.SHUTDOWN, requestId); }
        public static Outcome timeout() { return new Outcome(Type.TIMEOUT, null); }
    }
}
