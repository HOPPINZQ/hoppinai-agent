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
 * Glob工具的输入参数
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GlobInput {

    /**
     * Glob模式，支持通配符匹配
     * 例如: *.java, 双星号/*.json, test_*.py
     */
    @JsonProperty("pattern")
    private String pattern;

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }
}
