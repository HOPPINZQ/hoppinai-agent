package com.hoppinzq.agent.capability.compact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.session.SessionMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.ROOT;

/**
 * compact 能力自持的压缩历史存储。
 * <p>两份文件，均在 {@code <ROOT>/.transcripts/} 下：
 * <ul>
 *   <li>{@code transcript_<ts>.jsonl} —— 压缩前的完整对话快照（每次压缩一份）</li>
 *   <li>{@code records_<sessionId>.jsonl} —— 压缩元数据（JSON Lines，追加写，/transcripts 命令读取）</li>
 * </ul>
 * 压缩历史不进内核会话文件（SessionData），保证能力可整体复制拔除。
 *
 * @author hoppinzq
 */
public class TranscriptStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String sessionId;
    private final Path dir;

    public TranscriptStore(String sessionId) {
        this.sessionId = sessionId == null ? "default" : sessionId;
        this.dir = Paths.get(ROOT, ".transcripts");
    }

    /**
     * 保存压缩前的完整对话快照。
     *
     * @return 快照文件路径（供摘要消息引用）
     */
    public String saveFull(List<SessionMessage> messages) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("transcript_" + System.currentTimeMillis() + ".jsonl");
        List<String> lines = new ArrayList<>();
        for (SessionMessage msg : messages) {
            lines.add(MAPPER.writeValueAsString(msg));
        }
        Files.write(file, lines);
        return file.toString();
    }

    /**
     * 追加一条压缩元数据记录。
     */
    public void record(TranscriptRecord record) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("records_" + sessionId + ".jsonl");
        String line = MAPPER.writeValueAsString(record);
        if (Files.exists(file)) {
            Files.write(file, (line + "\n").getBytes(),
                    StandardOpenOption.APPEND);
        } else {
            Files.write(file, (line + "\n").getBytes());
        }
    }

    /**
     * 读取本会话的全部压缩元数据记录（文件不存在或损坏行跳过）。
     */
    public List<TranscriptRecord> list() {
        Path file = dir.resolve("records_" + sessionId + ".jsonl");
        List<TranscriptRecord> out = new ArrayList<>();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    out.add(MAPPER.readValue(line, TranscriptRecord.class));
                } catch (Exception ignore) {
                    // 损坏行跳过，不影响其余记录
                }
            }
        } catch (IOException e) {
            System.err.println("[压缩记录] 读取失败: " + e.getMessage());
        }
        return out;
    }
}
