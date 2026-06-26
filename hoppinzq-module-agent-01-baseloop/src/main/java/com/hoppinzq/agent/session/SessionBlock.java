package com.hoppinzq.agent.session;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 单个内容块的可序列化表示。
 * <p>支持三种类型：
 * <ul>
 *   <li>{@code text} —— 纯文本块，使用 {@link #text}</li>
 *   <li>{@code tool_use} —— 工具调用块，使用 {@link #toolUseId}/{@link #toolName}/{@link #toolInputJson}</li>
 *   <li>{@code tool_result} —— 工具结果块，使用 {@link #toolUseId}/{@link #toolResultContent}/{@link #isError}</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionBlock {
    /** 块类型：text / tool_use / tool_result */
    private String type;
    /** text 块的文本内容 */
    private String text;
    /** tool_use / tool_result 关联的工具调用 ID */
    private String toolUseId;
    /** tool_use 的工具名称 */
    private String toolName;
    /** tool_use 的输入（JSON 字符串，原样保留） */
    private String toolInputJson;
    /** tool_result 的内容 */
    private String toolResultContent;
    /** tool_result 是否为错误 */
    private Boolean isError;
}
