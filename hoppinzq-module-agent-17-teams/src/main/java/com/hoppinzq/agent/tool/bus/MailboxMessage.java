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
 * 邮箱消息 POJO。
 *
 * <p>{@code type} 字段取值：
 * <ul>
 *   <li>{@code message}：普通消息（teams 模块只用这种）</li>
 *   <li>{@code shutdown_request} / {@code shutdown_response}：预留，protocols 模块链用</li>
 *   <li>{@code plan_approval_request} / {@code plan_approval_response}：预留，protocols 模块链用</li>
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
