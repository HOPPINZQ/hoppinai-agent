package com.hoppinzq.agent.tool.memory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;

/**
 * 文件型持久记忆存储。
 * <p>
 * 目录结构：
 * <pre>
 * {ROOT}/.memory/
 *   ├── user_prefers_vim.md
 *   ├── feedback_no_emoji.md
 *   └── ...
 * {ROOT}/MEMORY.md          ← 索引，每条记忆一行
 * </pre>
 *
 * @author hoppinzq
 */
public class MemoryStore {

    public Path getMemoryDir() {
        return memoryDir;
    }

    /** 文件数阈值，超过触发 consolidate（合并 / 摘要） */
    public static final int CONSOLIDATE_THRESHOLD = 10;

    private final Path memoryDir;
    private final Path indexFile;
    private final AnthropicClient client;

    public MemoryStore(String root, AnthropicClient client) {
        this.memoryDir = Paths.get(root, ".memory");
        this.indexFile = Paths.get(root, "MEMORY.md");
        this.client = client;
        try {
            Files.createDirectories(memoryDir);
            if (!Files.exists(indexFile)) {
                Files.writeString(indexFile, "# Memory Index\n\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("初始化 MemoryStore 失败: " + e.getMessage(), e);
        }
    }

    /** 写入一条记忆，同步更新索引 */
    public synchronized void save(MemoryRecord record) {
        try {
            String name = sanitizeName(record.getName());
            Path file = memoryDir.resolve(record.getType() + "_" + name + ".md");
            Files.writeString(file, renderFile(record), StandardCharsets.UTF_8);
            upsertIndex(record.toIndexLine(), record.getType() + "_" + name);
        } catch (IOException e) {
            throw new RuntimeException("写入记忆失败: " + e.getMessage(), e);
        }
    }

    /** 读取索引全文 */
    public String readIndex() {
        try {
            return Files.readString(indexFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** 列出所有记忆文件名（不含路径） */
    public List<String> listFiles() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(memoryDir)) {
            return names;
        }
        try (Stream<Path> s = Files.list(memoryDir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException e) {
            // ignore
        }
        return names;
    }

    /** 文件数达到阈值，触发 consolidate（简单实现：调一次 LLM 合并索引） */
    public int fileCount() {
        return listFiles().size();
    }

    public String consolidateIfNeeded() {
        if (fileCount() < CONSOLIDATE_THRESHOLD) {
            return null;
        }
        return doConsolidate();
    }

    private String doConsolidate() {
        // 简化实现：保留所有记忆，仅打印提示。生产实现可调 LLM 合并近义条目。
        System.out.printf("\u001b[95m[memory]\u001b[0m 文件数已到 %d（>= %d），建议 consolidate%n",
                fileCount(), CONSOLIDATE_THRESHOLD);
        return "consolidate triggered (no-op in demo)";
    }

    // ============= 文件格式 =============

    private static final Pattern FRONTMATTER = Pattern.compile(
            "(?s)^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$");

    String renderFile(MemoryRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("type: ").append(r.getType()).append("\n");
        sb.append("name: ").append(sanitizeName(r.getName())).append("\n");
        sb.append("tags: [").append(String.join(", ", r.getTags())).append("]\n");
        sb.append("created: ").append(r.getCreated()).append("\n");
        sb.append("---\n\n");
        sb.append(r.getBody() == null ? "" : r.getBody());
        sb.append("\n");
        return sb.toString();
    }

    /** 解析 .memory/*.md 还原成 MemoryRecord */
    public MemoryRecord load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Matcher m = FRONTMATTER.matcher(text);
        if (!m.matches()) {
            return null;
        }
        String yaml = m.group(1);
        String body = m.group(2).trim();
        MemoryRecord r = new MemoryRecord();
        r.setBody(body);
        r.setType(extractYaml(yaml, "type"));
        r.setName(extractYaml(yaml, "name"));
        String created = extractYaml(yaml, "created");
        r.setCreated(created == null ? "" : created);
        r.setTags(parseTags(yaml));
        return r;
    }

    private static String extractYaml(String yaml, String key) {
        Pattern p = Pattern.compile("(?m)^" + key + ":\\s*(.+?)\\s*$");
        Matcher m = p.matcher(yaml);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static List<String> parseTags(String yaml) {
        Pattern p = Pattern.compile("(?m)^tags:\\s*\\[(.*?)\\]\\s*$");
        Matcher m = p.matcher(yaml);
        if (!m.find()) {
            return new ArrayList<>();
        }
        String body = m.group(1).trim();
        if (body.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> tags = new ArrayList<>();
        for (String part : body.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                tags.add(t);
            }
        }
        return tags;
    }

    // ============= 索引维护 =============

    private void upsertIndex(String line, String key) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(indexFile, StandardCharsets.UTF_8));
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(key + "]") || lines.get(i).contains(key + " ")) {
                // 粗略匹配同名条目并替换
                if (lines.get(i).startsWith("- [")) {
                    lines.set(i, line);
                    replaced = true;
                    break;
                }
            }
        }
        if (!replaced) {
            // 找到最后一条 - [ 开头的位置插入
            int insertAt = lines.size();
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (lines.get(i).startsWith("- [")) {
                    insertAt = i + 1;
                    break;
                }
            }
            if (insertAt > lines.size()) {
                insertAt = lines.size();
            }
            lines.add(insertAt, line);
        }
        Files.writeString(indexFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "untitled";
        }
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    // ============= 通用 LLM 调用 =============

    /**
     * 调一次 LLM；输入 prompt，返回纯文本响应。
     * 供 {@link MemorySelector} / {@link MemoryExtractor} 共用。
     */
    public String askLlm(String prompt) {
        MessageParam msg = MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(prompt)
                .build();
        Message message = client.messages().create(MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(1024)
                .temperature(0.0)
                .messages(List.of(msg))
                .build());
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : message.content()) {
            if (b.isText()) {
                TextBlock t = b.asText();
                sb.append(t.text());
            }
        }
        return sb.toString().trim();
    }

    static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(text.split("\\r?\\n"));
    }
}
