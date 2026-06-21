package com.hoppinzq.agent.tool.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆选择器：每轮 LLM 调用前，从 MEMORY.md 索引中挑出与当前 prompt 最相关的若干条。
 * <p>
 * 实现策略：调一次轻量 LLM，把索引全文喂进去，让模型返回相关的记忆名列表。
 * 拿到列表后从 .memory/ 读出正文，拼成注入到 system prompt 的"相关记忆"段。
 *
 * @author hoppinzq
 */
public class MemorySelector {

    /** 每轮最多注入 N 条记忆正文，避免上下文膨胀 */
    public static final int MAX_INJECT = 5;

    private static final Pattern NAME_PATTERN = Pattern.compile("([a-z]+_[a-zA-Z0-9_]+)");

    private final MemoryStore store;

    public MemorySelector(MemoryStore store) {
        this.store = store;
    }

    /**
     * 从索引中选择与 prompt 相关的记忆正文。
     *
     * @return 拼好的"相关记忆"段；无相关记忆返回空字符串
     */
    public String selectAndFormat(String userPrompt) {
        List<String> names = selectRelevantNames(userPrompt);
        if (names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String name : names) {
            String body = readBodyByName(name);
            if (body == null) {
                continue;
            }
            sb.append("- ").append(name).append(": ").append(body).append("\n");
            if (++count >= MAX_INJECT) {
                break;
            }
        }
        return sb.toString();
    }

    /** 调 LLM 从索引中挑出相关记忆名（type_name 形式） */
    List<String> selectRelevantNames(String userPrompt) {
        String index = store.readIndex();
        if (index == null || index.isBlank()) {
            return List.of();
        }
        String prompt = """
                下面是 agent 的记忆索引。请选出与用户当前问题最相关的记忆名（最多 %d 条）。
                只返回 type_name 形式，每行一个，不要任何额外说明；如果没有相关项，返回空。

                === 记忆索引 ===
                %s

                === 用户问题 ===
                %s
                """.formatted(MAX_INJECT, index, userPrompt);

        String answer;
        try {
            answer = store.askLlm(prompt);
        } catch (Exception e) {
            System.out.printf("\u001b[91m[memory select 失败]\u001b[0m %s%n", e.getMessage());
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String line : MemoryStore.splitLines(answer)) {
            Matcher m = NAME_PATTERN.matcher(line);
            if (m.find()) {
                String n = m.group(1);
                if (!names.contains(n)) {
                    names.add(n);
                }
            }
        }
        return names;
    }

    /** 根据记忆名读出正文（不返回 frontmatter） */
    private String readBodyByName(String name) {
        try {
            var files = store.listFiles();
            for (String fn : files) {
                if (fn.equals(name + ".md")) {
                    java.nio.file.Path p = java.nio.file.Paths.get(
                            store.getMemoryDir().toString(), fn);
                    MemoryRecord r = store.load(p);
                    return r == null ? null : r.getBody();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
