package com.hoppinzq.agent.tool.bus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮箱消息 POJO。每条消息以一行 JSON 写入 {@code <ROOT>/.mailboxes/{name}.jsonl}。
 *
 * <p>消息类型约定：
 * <ul>
 *   <li><b>message</b>：普通文本消息，由 teammate 当作 user 输入注入对话</li>
 *   <li><b>shutdown_request</b>：lead 请求 teammate 关闭，payload 中携带 requestId</li>
 *   <li><b>shutdown_response</b>：teammate 回复同意关闭</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailboxMessage {

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;

    @JsonProperty("content")
    private String content;

    @JsonProperty("type")
    private String type;

    @JsonProperty("ts")
    private long ts;
}
