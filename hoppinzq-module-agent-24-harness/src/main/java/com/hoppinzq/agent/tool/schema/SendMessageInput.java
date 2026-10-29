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
 * <p>
 * teammate 用此工具向 lead 或其它 teammate 发消息，{@code from} 由 Tools 里的
 * {@code ThreadLocal<String> CURRENT_TEAMMATE_NAME} 自动填入。
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

    @JsonProperty("type")
    @Builder.Default
    private String type = "message";

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }
}
