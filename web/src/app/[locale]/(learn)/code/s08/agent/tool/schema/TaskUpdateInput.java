package com.hoppinzq.agent.tool.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.JSON_FAIL;
import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * task_update工具输入参数
 *
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskUpdateInput {

    @JsonProperty("taskId")
    private Integer taskId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("addBlockedBy")
    @JsonDeserialize(using = IntegerListDeserializer.class)
    private List<Integer> addBlockedBy;

    @JsonProperty("addBlocks")
    @JsonDeserialize(using = IntegerListDeserializer.class)
    private List<Integer> addBlocks;

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }

    /**
     * 整数列表反序列化器，支持多种输入格式
     */
    public static class IntegerListDeserializer extends JsonDeserializer<List<Integer>> {
        @Override
        public List<Integer> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();
            if (token == JsonToken.START_ARRAY) {
                return p.readValueAs(new TypeReference<List<Integer>>() {
                });
            }
            if (token == JsonToken.VALUE_NUMBER_INT) {
                List<Integer> single = new ArrayList<>();
                single.add(p.getIntValue());
                return single;
            }
            if (token == JsonToken.VALUE_NULL) {
                return new ArrayList<>();
            }
            if (token == JsonToken.VALUE_STRING) {
                String raw = p.getValueAsString();
                if (raw == null || raw.trim().isEmpty()) {
                    return new ArrayList<>();
                }
                String text = raw.trim();
                // 移除可能的字段前缀
                if (text.contains("=")) {
                    text = text.substring(text.indexOf("=") + 1).trim();
                }
                if (text.startsWith("[") && text.endsWith("]")) {
                    return OBJECT_MAPPER.readValue(text, new TypeReference<List<Integer>>() {
                    });
                }
                // 尝试解析单个数字
                try {
                    List<Integer> single = new ArrayList<>();
                    single.add(Integer.parseInt(text));
                    return single;
                } catch (NumberFormatException e) {
                    // 忽略，返回空列表
                    return new ArrayList<>();
                }
            }
            throw ctxt.mappingException("任务ID列表需要是 array/number/stringified-json 格式, got: " + token);
        }
    }
}
