package com.hoppinzq.agent.tool.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.hoppinzq.agent.constant.AIConstants.JSON_FAIL;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

import java.util.Map;

/**
 * 一次协议交互的状态机记录。
 * <p>
 * 状态取值（{@link #status}）：
 * <ul>
 *   <li>{@code pending}：请求已创建，等待对端响应</li>
 *   <li>{@code approved}：审批类请求被通过（plan_approval）</li>
 *   <li>{@code rejected}：审批类请求被驳回</li>
 *   <li>{@code responded}：对端已回应（shutdown_response 等）</li>
 * </ul>
 *
 * 类型取值（{@link #type}）：
 * <ul>
 *   <li>{@code shutdown_request} / {@code shutdown_response}</li>
 *   <li>{@code plan_approval_request} / {@code plan_approval_response}</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtocolState {
    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("target")
    private String target;

    /** pending / approved / rejected / responded */
    @JsonProperty("status")
    private String status;

    /** 灵活负载，用 Map&lt;String,Object&gt; 保存 requestId、plan、approved、comment 等 */
    @JsonProperty("payload")
    private Map<String, Object> payload;

    @JsonProperty("createdAt")
    private long createdAt;

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }
}
