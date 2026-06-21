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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoInput {

    @JsonProperty("todos")
    @JsonDeserialize(using = TodoItemsDeserializer.class)
    private List<TodoItem> todos;

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return JSON_FAIL;
        }
    }

    public static class TodoItemsDeserializer extends JsonDeserializer<List<TodoItem>> {
        @Override
        public List<TodoItem> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();
            if (token == JsonToken.START_ARRAY) {
                return p.readValueAs(new TypeReference<List<TodoItem>>() {});
            }
            if (token == JsonToken.START_OBJECT) {
                TodoItem item = p.readValueAs(TodoItem.class);
                List<TodoItem> single = new ArrayList<>();
                single.add(item);
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
                if (text.startsWith("todos=")) {
                    text = text.substring("todos=".length()).trim();
                }
                if (text.startsWith("[") && text.endsWith("]")) {
                    return OBJECT_MAPPER.readValue(text, new TypeReference<List<TodoItem>>() {});
                }
                if (text.startsWith("{") && text.endsWith("}")) {
                    TodoItem item = OBJECT_MAPPER.readValue(text, TodoItem.class);
                    List<TodoItem> single = new ArrayList<>();
                    single.add(item);
                    return single;
                }
            }
            throw ctxt.mappingException("待办列表需要是 array/object/stringified-json 格式, got: " + token);
        }
    }
}
