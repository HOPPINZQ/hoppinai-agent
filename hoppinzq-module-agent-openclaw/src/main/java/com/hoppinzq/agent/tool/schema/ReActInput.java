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
 * ReAct 输入参数
 * ReAct 是一种结合推理和行动的 AI 智能体框架
 *
 * @author hoppinzq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActInput {
    /**
     * 思考过程：AI 对当前情况的分析和下一步行动计划的思考
     */
    @JsonProperty("thought")
    private String thought;

    /**
     * 行动：要执行的工具名称
     */
    @JsonProperty("action")
    private String action;

    /**
     * 行动输入：工具参数，必须是 JSON 对象格式
     */
    @JsonProperty("actionInput")
    private String actionInput;

    /**
     * 观察结果：工具执行的返回结果
     */
    @JsonProperty("observation")
    private String observation;

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }
}
