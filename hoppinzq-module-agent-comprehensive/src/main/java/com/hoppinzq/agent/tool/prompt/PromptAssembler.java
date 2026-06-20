package com.hoppinzq.agent.tool.prompt;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 系统提示词装配器。
 * <p>
 * 4 个 section（按注册顺序渲染）：
 * <ol>
 *   <li><b>identity</b>     — agent 身份定义</li>
 *   <li><b>tools</b>        — 工具列表（按当前 tools 动态生成）</li>
 *   <li><b>workspace</b>    — 工作目录、操作系统</li>
 *   <li><b>memory</b>       — 仅当 MEMORY.md 存在时挂上；读取索引摘要</li>
 * </ol>
 *
 * <p>{@link #get(AgentContext)} 带 context-hash 缓存：相同 context 只装配一次。
 *
 * @author hoppinzq
 */
public class PromptAssembler {

    /** 单个 section：name + 是否启用的谓词 + 渲染函数 */
    @Data
    @AllArgsConstructor
    public static class Section {
        private final String name;
        private final Function<AgentContext, Boolean> enabled;
        private final Function<AgentContext, String> render;
    }

    private final List<Section> sections = new ArrayList<>();
    private final Map<String, String> cache = new HashMap<>();
    private String lastKey;
    private String lastValue;

    public PromptAssembler() {
        // 默认注册 4 段
        register("identity", ctx -> true, this::renderIdentity);
        register("tools", ctx -> ctx.getToolNames() != null && !ctx.getToolNames().isEmpty(),
                this::renderTools);
        register("workspace", ctx -> true, this::renderWorkspace);
        register("memory", AgentContext::isMemoryEnabled, this::renderMemory);
    }

    public void register(String name, Function<AgentContext, Boolean> enabled,
                         Function<AgentContext, String> render) {
        sections.add(new Section(name, enabled, render));
        cache.clear();
    }

    /** 装配当前 context 的系统提示词；同 context 走缓存。 */
    public String get(AgentContext ctx) {
        String key = ctx.cacheKey();
        if (key.equals(lastKey)) {
            return lastValue;
        }
        if (cache.containsKey(key)) {
            lastKey = key;
            lastValue = cache.get(key);
            return lastValue;
        }
        StringBuilder sb = new StringBuilder();
        for (Section s : sections) {
            try {
                if (Boolean.TRUE.equals(s.getEnabled().apply(ctx))) {
                    String block = s.getRender().apply(ctx);
                    if (block != null && !block.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(block);
                    }
                }
            } catch (Exception e) {
                System.out.printf("\u001b[91m[prompt section %s 异常]\u001b[0m %s%n",
                        s.getName(), e.getMessage());
            }
        }
        lastKey = key;
        lastValue = sb.toString();
        cache.put(key, lastValue);
        return lastValue;
    }

    // ============= 默认 section 实现 =============

    private String renderIdentity(AgentContext ctx) {
        return """
                ## 角色
                你是一个动态装配系统提示词的开发助手。系统提示由 PromptAssembler 在每轮按 context
                条件渲染（identity / tools / workspace / memory），不写死在代码里。""";
    }

    private String renderTools(AgentContext ctx) {
        StringBuilder sb = new StringBuilder("## 可用工具\n");
        for (String name : ctx.getToolNames()) {
            sb.append("- ").append(name).append("\n");
        }
        return sb.toString().trim();
    }

    private String renderWorkspace(AgentContext ctx) {
        return String.format("""
                ## 工作环境
                - 操作系统：%s
                - 工作目录：%s""", ctx.getOsName(), ctx.getWorkspace());
    }

    private String renderMemory(AgentContext ctx) {
        if (ctx.getWorkspace() == null) {
            return null;
        }
        Path memoryFile = Paths.get(ctx.getWorkspace(), "MEMORY.md");
        if (!Files.isRegularFile(memoryFile)) {
            return null;
        }
        try {
            String text = Files.readString(memoryFile).trim();
            if (text.isEmpty()) {
                return null;
            }
            // 只截前 1500 字符进 system，避免污染
            if (text.length() > 1500) {
                text = text.substring(0, 1500) + "\n... (MEMORY.md 已截断)";
            }
            return "## 持久记忆索引\n" + text;
        } catch (Exception e) {
            return null;
        }
    }
}
