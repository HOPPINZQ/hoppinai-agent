package com.hoppinzq.agent.tool.bus;

import com.hoppinzq.agent.constant.AIConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于文件的消息总线。
 *
 * <p>每个 teammate / lead 有自己的收件箱 {@code <ROOT>/.mailboxes/{name}.jsonl}。
 * 调用 {@link #send(String, String, String, String)} 时把消息追加到收件人文件末尾；
 * 调用 {@link #readInbox(String)} 时一次性读出全部消息然后清空文件，
 * 即「读即消费」语义，同一条消息不会被消费两次。
 *
 * <p>所有方法都是静态的，无需实例化（保留无参构造仅为符合 JavaBean 习惯，
 * AgentTeams.main 中可以 {@code new MessageBus()} 注入到 ZQAgent）。
 *
 * @author hoppinzq
 */
public class MessageBus {

    public MessageBus() {
        // 无状态，仅作为依赖注入的占位对象
    }

    /** 返回某 name 对应的收件箱文件路径。 */
    public static Path inboxPath(String name) {
        return Paths.get(AIConstants.ROOT, ".mailboxes", name + ".jsonl");
    }

    /**
     * 把一条消息追加到收件人的收件箱文件末尾（不存在则创建）。
     */
    public static void send(String from, String to, String content, String type) {
        try {
            Path path = inboxPath(to);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            MailboxMessage msg = MailboxMessage.builder()
                    .from(from)
                    .to(to)
                    .content(content)
                    .type(type == null ? "message" : type)
                    .ts(System.currentTimeMillis())
                    .build();
            String line = AIConstants.OBJECT_MAPPER.writeValueAsString(msg) + "\n";
            Files.write(path, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.printf("\u001b[95m[bus]\u001b[0m %s -> %s [%s]: %s%n",
                    from, to, msg.getType(),
                    content.length() > 80 ? content.substring(0, 80) + "..." : content);
        } catch (Exception e) {
            System.err.println("[bus] send 失败: " + e.getMessage());
        }
    }

    /**
     * 一次性读出全部消息然后清空文件（读即消费）。
     * 文件不存在或解析失败时返回空列表。
     */
    public static List<MailboxMessage> readInbox(String name) {
        List<MailboxMessage> out = new ArrayList<>();
        Path path = inboxPath(name);
        if (!Files.exists(path)) {
            return out;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                try {
                    out.add(AIConstants.OBJECT_MAPPER.readValue(line, MailboxMessage.class));
                } catch (Exception ignore) {
                    // 单行解析失败不影响其它行
                }
            }
            // 清空文件，防止重复消费
            try {
                Files.write(path, new byte[0],
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignore) {
                // 清空失败不影响读取结果
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return out;
    }
}
