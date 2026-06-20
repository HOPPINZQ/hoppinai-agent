package com.hoppinzq.agent.tool.bus;

import com.hoppinzq.agent.constant.AIConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * 进程内消息总线：每个成员（lead / teammate）对应一个 &lt;ROOT&gt;/.mailboxes/{name}.jsonl 文件。
 * <ul>
 *   <li>{@link #send} 把一条 {@link MailboxMessage} 序列化为单行 JSON 追加到接收方的 jsonl 文件</li>
 *   <li>{@link #readInbox} 读取并清空调用方自己的 inbox（消费即删）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class MessageBus {

    /**
     * 向 {@code to} 的 mailbox 追加一条消息。
     */
    public static void send(String from, String to, String content, String type) {
        try {
            MailboxMessage msg = MailboxMessage.builder()
                    .from(from)
                    .to(to)
                    .content(content)
                    .type(type)
                    .ts(System.currentTimeMillis())
                    .build();
            Path path = inboxPath(to);
            Files.createDirectories(path.getParent());
            String line = OBJECT_MAPPER.writeValueAsString(msg);
            Files.write(path, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[bus] send 失败 from=" + from + " to=" + to + " : " + e.getMessage());
        }
    }

    /**
     * 读取 {@code name} 的 inbox 里所有消息，并清空文件（消费语义）。
     */
    public static List<MailboxMessage> readInbox(String name) {
        List<MailboxMessage> result = new ArrayList<>();
        Path path = inboxPath(name);
        if (!Files.exists(path)) {
            return result;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                try {
                    result.add(OBJECT_MAPPER.readValue(line, MailboxMessage.class));
                } catch (Exception ignore) {
                    // 跳过坏行
                }
            }
            // 清空 inbox
            Files.write(path, new byte[0], java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("[bus] readInbox 失败 name=" + name + " : " + e.getMessage());
        }
        return result;
    }

    /**
     * 返回 {@code name} 对应的 jsonl inbox 路径。
     */
    public static Path inboxPath(String name) {
        return Paths.get(AIConstants.ROOT, ".mailboxes", name + ".jsonl");
    }
}
