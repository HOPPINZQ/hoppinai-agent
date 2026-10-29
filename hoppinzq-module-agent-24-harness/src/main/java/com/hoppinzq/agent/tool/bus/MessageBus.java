package com.hoppinzq.agent.tool.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.constant.AIConstants;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 进程内消息总线。所有 teammate 共享同一个 {@link MessageBus} 单例，
 * 通过 mailbox 文件（{@code <ROOT>/.mailboxes/{name}.jsonl}）异步投递消息。
 *
 * <p>为什么用文件而不是内存队列：和 Python 教程保持一致，便于跨进程调试 / 持久化观察。
 *
 * @author hoppinzq
 */
@Slf4j
public class MessageBus {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path mailboxesRoot;

    public MessageBus() {
        this.mailboxesRoot = Paths.get(AIConstants.ROOT, ".mailboxes");
        try {
            Files.createDirectories(this.mailboxesRoot);
        } catch (IOException e) {
            log.error("创建 mailbox 目录失败", e);
        }
    }

    /**
     * 发送一条消息到 {@code to} 的 mailbox 文件（追加一行 JSON）。
     *
     * @param from    发送者名字（lead / teammate name）
     * @param to      接收者名字
     * @param content 消息正文
     * @param type    消息类型：message / shutdown_request / shutdown_response
     */
    public void send(String from, String to, String content, String type) {
        MailboxMessage msg = MailboxMessage.builder()
                .from(from)
                .to(to)
                .content(content)
                .type(type)
                .ts(System.currentTimeMillis())
                .build();
        try {
            String line = MAPPER.writeValueAsString(msg);
            Files.createDirectories(inboxPath(to).getParent());
            Files.write(inboxPath(to),
                    (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("写入 mailbox 失败 from={} to={}", from, to, e);
            throw new RuntimeException("写入 mailbox 失败", e);
        }
    }

    /**
     * 读取并清空 {@code name} 的 mailbox。返回按时间戳升序的消息列表。
     * 读后即清，避免 teammate 反复处理同一条消息。
     */
    public List<MailboxMessage> readInbox(String name) {
        Path inbox = inboxPath(name);
        if (!Files.exists(inbox)) {
            return Collections.emptyList();
        }
        List<MailboxMessage> out = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(inbox, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                try {
                    out.add(MAPPER.readValue(line, MailboxMessage.class));
                } catch (Exception parseErr) {
                    log.warn("解析 mailbox 行失败: {}", line, parseErr);
                }
            }
            // 读后清空
            Files.write(inbox, new byte[0],
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("读取 mailbox 失败 name={}", name, e);
        }
        out.sort((a, b) -> Long.compare(a.getTs(), b.getTs()));
        return out;
    }

    /**
     * 返回某个 teammate 的 mailbox 文件路径。
     */
    public Path inboxPath(String name) {
        return mailboxesRoot.resolve(name + ".jsonl");
    }
}
