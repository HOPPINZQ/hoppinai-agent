package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * ToolDefinition 工具定义类。
 * <p>本模块无基座工具静态字段：bash 工具是沙箱特性的载体，
 * 已随能力化移入 {@code capability/sandbox/SandboxCapability#tools()}。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinition {
    // toolCall 或者 MCP需要用的三个字段
    private String name;
    private String description;
    /** 协议中立的 JSON Schema（type/properties/required），由 Provider 转为各自 SDK 类型 */
    private ObjectNode inputSchema;
    // 工具的参数类型和处理函数
    private Class<?> type;
    private Function<String, String> function;
    private TypedToolInvoker typedInvoker;

    public ToolDefinition(String name, String description, ObjectNode inputSchema, Class<?> type, Function<String, String> function) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.type = type;
        this.function = function;
        this.typedInvoker = null;
    }

    public <T> ToolDefinition(String name, String description, ObjectNode inputSchema, Class<T> type, TypedToolFunction<T> typedFunction) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.type = type;
        this.function = null;
        this.typedInvoker = input -> typedFunction.apply(type.cast(input));
    }

    /**
     * 创建属性定义
     */
    public static Map<String, Object> createProperty(String type, String description) {
        Map<String, Object> property = new HashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    /**
     * 创建协议中立的 InputSchema（JSON Schema：type/properties/required），
     * 由 Provider 实现类负责转为各自 SDK 的 schema 类型。
     */
    public static ObjectNode createInputSchema(Map<String, Object> properties, List<String> required) {
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", OBJECT_MAPPER.valueToTree(properties));
        if (required != null && !required.isEmpty()) {
            schema.set("required", OBJECT_MAPPER.valueToTree(required));
        }
        return schema;
    }

    public String invoke(Object convertedInput) throws Exception {
        if (typedInvoker != null) {
            return typedInvoker.apply(convertedInput);
        }
        if (function != null) {
            if (convertedInput instanceof String) {
                return function.apply((String) convertedInput);
            }
            return function.apply(OBJECT_MAPPER.writeValueAsString(convertedInput));
        }
        throw new IllegalStateException("未配置工具处理器: " + name);
    }

    @FunctionalInterface
    public interface TypedToolFunction<T> {
        String apply(T input) throws Exception;
    }

    @FunctionalInterface
    public interface TypedToolInvoker {
        String apply(Object input) throws Exception;
    }
}
