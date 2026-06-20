package com.hoppinzq.agent.tool.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.hoppinzq.agent.constant.AIConstants.JSON_FAIL;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * send_message 工具入参。
 *
 * <p>{@code from} 字段由调用方通过 {@code ThreadLocal} 决定：
 * 在 lead 主线程里是 {@code "lead"}，在 teammate 守护线程里是 teammate 名字。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageInput {
    @JsonProperty("to")
    private String to;

    @JsonProperty("content")
    private String content;

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }
}
