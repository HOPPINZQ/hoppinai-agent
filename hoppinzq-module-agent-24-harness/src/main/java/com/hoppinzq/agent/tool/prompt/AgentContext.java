package com.hoppinzq.agent.tool.prompt;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 系统提示词装配上下文。
 * <p>
 * 决定 {@link PromptAssembler} 装配哪些 section —— 例如 MEMORY.md 不存在就不挂 memory 段。
 *
 * @author hoppinzq
 */
@Data
@Builder
public class AgentContext {
    /** 工作目录绝对路径 */
    private String workspace;
    /** 操作系统名（小写） */
    private String osName;
    /** 当前已注册的工具名列表 */
    private List<String> toolNames;
    /** MEMORY.md 是否存在（决定是否挂 memory 段） */
    private boolean memoryEnabled;
    /** 用户当前 prompt（用于条件渲染示例） */
    private String userPrompt;

    /**
     * 上下文 hash —— 用来决定是否需要重新装配（缓存键）。
     * 只覆盖影响系统提示的几个字段，userPrompt 不算（userPrompt 进 message，不进 system）。
     */
    public String cacheKey() {
        return (workspace == null ? "" : workspace) + "|"
                + (osName == null ? "" : osName) + "|"
                + (toolNames == null ? "" : String.join(",", toolNames)) + "|"
                + memoryEnabled;
    }
}
