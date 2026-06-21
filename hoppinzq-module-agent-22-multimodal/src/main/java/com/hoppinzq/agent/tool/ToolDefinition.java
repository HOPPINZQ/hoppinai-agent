package com.hoppinzq.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.tool.schema.BashInput;
import com.hoppinzq.agent.tool.schema.ReadFileInput;
import com.hoppinzq.agent.tool.schema.ScreenshotInput;
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
 * s22 工具定义。仅在 s01 的 bash 基础上增加 read_file / screenshot 两个工具。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinition {
    public static ToolDefinition BashDefinition = new ToolDefinition(
            "bash",
            "执行 Shell 命令并返回其输出结果。",
            createInputSchema(
                    Map.of(
                            "command", createProperty("string", "要执行的命令字符串。"),
                            "type", createProperty("string", "命令类型：cmd / powershell / bash。")
                    ),
                    List.of("command")
            ),
            BashInput.class,
            Tools::executeBash
    );

    /**
     * read_file：增强版，识别图片 MIME 自动 base64。
     */
    public static ToolDefinition ReadFileDefinition = new ToolDefinition(
            "read_file",
            "读取指定文件路径。若为图片（image/*）自动转 base64 image block；若为文本返回字符串；其他二进制返回文件描述。",
            createInputSchema(
                    Map.of("path", createProperty("string", "工作目录中文件的相对路径。")),
                    List.of("path")
            ),
            ReadFileInput.class,
            Tools::readFile
    );

    /**
     * screenshot：抓取当前屏幕，返回 base64 PNG。
     */
    public static ToolDefinition ScreenshotDefinition = new ToolDefinition(
            "screenshot",
            "抓取当前屏幕截图，返回 base64 编码的 PNG。适用于需要查看屏幕内容的场景。",
            createInputSchema(
                    Map.of(),
                    List.of()
            ),
            ScreenshotInput.class,
            Tools::screenshot
    );

    private String name;
    private String description;
    private Tool.InputSchema inputSchema;
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

    public static Map<String, Object> createProperty(String type, String description) {
        Map<String, Object> property = new HashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    public static Tool.InputSchema createInputSchema(Map<String, Object> properties, List<String> required) {
        ObjectNode propertiesNode = OBJECT_MAPPER.valueToTree(properties);
        Tool.InputSchema.Builder schemaBuilder = Tool.InputSchema.builder()
                .properties(JsonValue.fromJsonNode(propertiesNode));
        if (required != null && !required.isEmpty()) {
            schemaBuilder.required(required);
        }
        return schemaBuilder.build();
    }
}
