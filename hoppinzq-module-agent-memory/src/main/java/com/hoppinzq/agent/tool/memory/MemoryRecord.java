package com.hoppinzq.agent.tool.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 单条记忆记录。对应磁盘文件 `.memory/{type}_{name}.md`。
 * <p>
 * 文件格式（YAML frontmatter + 正文）：
 * <pre>
 * ---
 * type: user
 * name: prefers_vim
 * tags: [editor, vim]
 * created: 2026-06-19T10:15:30Z
 * ---
 * 用户偏好使用 vim 编辑器。
 * </pre>
 *
 * <p>四类记忆：
 * <ul>
 *   <li><b>user</b>      — 用户偏好/身份</li>
 *   <li><b>feedback</b>  — 用户纠正过的错误做法</li>
 *   <li><b>project</b>   — 项目级约定（目录、技术栈、CI 等）</li>
 *   <li><b>reference</b> — 外部资料链接/路径</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecord {
    public static final String TYPE_USER = "user";
    public static final String TYPE_FEEDBACK = "feedback";
    public static final String TYPE_PROJECT = "project";
    public static final String TYPE_REFERENCE = "reference";

    private String type;
    /** 记忆标识（用作文件名一部分）；只允许 [a-zA-Z0-9_] */
    private String name;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    @Builder.Default
    private String created = Instant.now().toString();
    /** 正文 */
    private String body;

    /** 一行摘要，写入 MEMORY.md 索引 */
    public String toIndexLine() {
        String tagStr = tags.isEmpty() ? "" : " #" + String.join(" #", tags);
        String bodyBrief = body == null ? "" :
                body.replaceAll("\\s+", " ").trim();
        if (bodyBrief.length() > 80) {
            bodyBrief = bodyBrief.substring(0, 80) + "...";
        }
        return String.format("- [%s] %s%s — %s", type, name, tagStr, bodyBrief);
    }
}
