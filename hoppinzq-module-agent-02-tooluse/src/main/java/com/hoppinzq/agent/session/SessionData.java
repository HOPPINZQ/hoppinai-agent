package com.hoppinzq.agent.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话持久化数据：消息列表 + token 使用统计。
 * <p>这是 {@code .sessions/<id>.json} 的根结构，便于扩展更多元数据。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionData {
    /**
     * 消息列表（兼容旧版本的纯列表格式）
     */
    @Builder.Default
    private List<SessionMessage> messages = new ArrayList<>();

    /**
     * token 使用统计（每次 LLM 调用的明细）
     */
    @Builder.Default
    private List<TokenUsage> usage = new ArrayList<>();

    /**
     * 创建此会话时的时间戳（可选，用于会话排序）
     */
    private String createdAt;

    /**
     * 会话最后更新时间（可选）
     */
    private String updatedAt;
}
