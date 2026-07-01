package com.hoppinzq.agent.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一条聊天消息的可序列化表示。
 * <p>对应 SDK 的 {@code MessageParam}。两种形态：
 * <ul>
 *   <li>纯字符串内容（常见于 user 输入）：使用 {@link #text}</li>
 *   <li>结构化块内容（assistant 回复、tool_result 回灌）：使用 {@link #blocks}</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionMessage {
    /**
     * 角色：user / assistant
     */
    private String role;
    /**
     * 字符串内容；非空表示纯字符串消息
     */
    private String text;
    /**
     * 结构化内容；非空表示块消息
     */
    private List<SessionBlock> blocks;
}
