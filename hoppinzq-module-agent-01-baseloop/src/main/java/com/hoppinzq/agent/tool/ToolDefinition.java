package com.hoppinzq.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.tool.schema.BashInput;
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
 * ToolDefinition 工具定义类
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinition {
    /**
     * Bash 命令执行工具定义
     * 执行 Shell 命令并返回输出结果。支持多种命令类型，适用于文件操作、程序执行、系统信息查询等场景。
     */
    public static ToolDefinition BashDefinition = new ToolDefinition(
            "bash",
            "执行 Shell 命令并返回其输出结果。适用于需要运行各种 Shell 命令的场景，可用于文件操作、程序执行、系统信息查询等多种任务。比如：如果用户让你打开网站，直接使用 start [url]；你要进入目录，如果返回系统找不到指定的路径，则尝试一下绝对路径。始终记住，你处于 " + System.getProperty("os.name").toLowerCase() + " 操作系统。",
            createInputSchema(
                    Map.of(
                            "command", createProperty("string", "要执行的命令字符串。"),
                            "type", createProperty("string", "命令类型，可选值：cmd（Windows CMD）、powershell（Windows PowerShell）、bash（Linux/Mac）。如不指定，系统将根据操作系统自动选择。")
                    ),
                    List.of("command", "type")
            ),
            BashInput.class,
            Tools::executeBash
    );
    // toolCall 或者 MCP需要用的三个字段
    private String name;
    private String description;
    private Tool.InputSchema inputSchema;
    // 工具的参数类型和处理函数
    private Class<?> type;
    private Function<String, String> function;
    private TypedToolInvoker typedInvoker;

    public ToolDefinition(String name, String description, Tool.InputSchema inputSchema, Class<?> type, Function<String, String> function) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.type = type;
        this.function = function;
        this.typedInvoker = null;
    }

    public <T> ToolDefinition(String name, String description, Tool.InputSchema inputSchema, Class<T> type, TypedToolFunction<T> typedFunction) {
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
     * 创建 InputSchema
     */
    public static Tool.InputSchema createInputSchema(Map<String, Object> properties, List<String> required) {
        ObjectNode propertiesNode = OBJECT_MAPPER.valueToTree(properties);

        Tool.InputSchema.Builder schemaBuilder = Tool.InputSchema.builder()
                .properties(JsonValue.fromJsonNode(propertiesNode));

        if (required != null && !required.isEmpty()) {
            schemaBuilder.required(required);
        }

        return schemaBuilder.build();
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
