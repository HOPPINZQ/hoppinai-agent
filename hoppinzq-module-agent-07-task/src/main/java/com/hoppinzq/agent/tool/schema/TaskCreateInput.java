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
 * task_create工具输入参数
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskCreateInput {

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("description")
    private String description;

    @JsonProperty("blockedBy")
    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> blockedBy;

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }

    /**
     * 字符串列表反序列化器，支持多种输入格式
     */
    public static class StringListDeserializer extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();
            if (token == JsonToken.START_ARRAY) {
                return p.readValueAs(new TypeReference<List<String>>() {});
            }
            if (token == JsonToken.VALUE_STRING) {
                String val = p.getValueAsString();
                if (val == null || val.trim().isEmpty()) {
                    return new ArrayList<>();
                }
                List<String> result = new ArrayList<>();
                result.add(val.trim());
                return result;
            }
            if (token == JsonToken.VALUE_NULL) {
                return new ArrayList<>();
            }
            throw ctxt.mappingException("任务ID列表需要是 array/string 格式, got: " + token);
        }
    }
}
