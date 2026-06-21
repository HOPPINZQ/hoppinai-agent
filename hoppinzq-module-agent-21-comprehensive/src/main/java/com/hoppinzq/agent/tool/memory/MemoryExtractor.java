package com.hoppinzq.agent.tool.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆抽取器：每轮工具循环结束后，调用一次 LLM 从本轮对话中抽取新记忆，
 * 写入 .memory/。抽取的格式约定（让 LLM 输出便于解析）：
 * <pre>
 * TYPE|name|tag1,tag2|body line
 * </pre>
 * 示例：
 * <pre>
 * user|prefers_vim|editor,vim|用户偏好使用 vim 编辑器
 * feedback|no_emoji|style|不要在回复中使用 emoji
 * </pre>
 *
 * @author hoppinzq
 */
public class MemoryExtractor {

    private static final Pattern LINE = Pattern.compile(
            "^(user|feedback|project|reference)\\|([^|]+)\\|([^|]*)\\|(.+)$");

    private final MemoryStore store;

    public MemoryExtractor(MemoryStore store) {
        this.store = store;
    }

    /**
     * 从一段对话总结（user prompt + 最近 assistant 回复）中抽取记忆并落盘。
     *
     * @return 实际写入的记忆数
     */
    public int extractAndSave(String userPrompt, String assistantSummary) {
        String prompt = """
                从下面的对话片段中抽取值得长期记住的事实，按下面格式每行一条输出，不要任何额外说明。
                如果没有值得记的，输出空。

                格式：
                TYPE|name|tag1,tag2|body
                TYPE 取值：user（用户偏好/身份）、feedback（用户纠正过的错误做法）、
                          project（项目级约定）、reference（外部链接/路径）
                name 只允许小写字母/数字/下划线，简短唯一
                tag1,tag2 可为空
                body 一句话描述

                === 对话 ===
                用户：%s
                助手：%s
                """.formatted(safe(userPrompt), safe(assistantSummary));

        String answer;
        try {
            answer = store.askLlm(prompt);
        } catch (Exception e) {
            System.out.printf("\u001b[91m[memory extract 失败]\u001b[0m %s%n", e.getMessage());
            return 0;
        }

        int saved = 0;
        for (String raw : MemoryStore.splitLines(answer)) {
            Matcher m = LINE.matcher(raw.trim());
            if (!m.matches()) {
                continue;
            }
            String type = m.group(1);
            String name = m.group(2).trim();
            String tagPart = m.group(3).trim();
            String body = m.group(4).trim();
            if (name.isEmpty() || body.isEmpty()) {
                continue;
            }
            List<String> tags = new ArrayList<>();
            if (!tagPart.isEmpty()) {
                for (String t : tagPart.split(",")) {
                    String tt = t.trim();
                    if (!tt.isEmpty()) {
                        tags.add(tt);
                    }
                }
            }
            try {
                store.save(MemoryRecord.builder()
                        .type(type).name(name).tags(tags).body(body).build());
                System.out.printf("\u001b[95m[memory 已保存]\u001b[0m %s_%s%n", type, name);
                saved++;
            } catch (Exception e) {
                System.out.printf("\u001b[91m[memory 保存失败 %s_%s]\u001b[0m %s%n",
                        type, name, e.getMessage());
            }
        }
        return saved;
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.length() > 1500 ? s.substring(0, 1500) + "..." : s;
    }
}
