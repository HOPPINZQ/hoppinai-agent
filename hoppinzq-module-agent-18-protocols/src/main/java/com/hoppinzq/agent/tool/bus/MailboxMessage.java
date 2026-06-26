package com.hoppinzq.agent.tool.bus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.hoppinzq.agent.constant.AIConstants.JSON_FAIL;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * 一条投递到 mailbox 的消息（teams / protocols 模块共用）。
 * <p>
 * 字段含义：
 * <ul>
 *   <li>{@code from} / {@code to}：发送方 / 接收方名字（lead 或 teammate 名字）</li>
 *   <li>{@code content}：消息正文（文本或 JSON 字符串）</li>
 *   <li>{@code type}：消息类型，常见值：{@code message}、{@code shutdown_request}、
 *       {@code shutdown_response}、{@code plan_approval_request}、{@code plan_approval_response}</li>
 *   <li>{@code ts}：发送时间戳（毫秒）</li>
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

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }
}
