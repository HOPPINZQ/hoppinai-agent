package com.hoppinzq.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.Agent03;
import com.hoppinzq.agent.tool.schema.*;
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
    private Tool.InputSchema inputSchema;

    // 工具的参数类型和处理函数
    private Class<?> type;
    private Function<String, String> function;
    private TypedToolInvoker typedInvoker;

    @FunctionalInterface
    public interface TypedToolFunction<T> {
        String apply(T input) throws Exception;
    }

    @FunctionalInterface
    public interface TypedToolInvoker {
        String apply(Object input) throws Exception;
    }

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

    public static ToolDefinition ReadFileDefinition = new ToolDefinition(
            "read_file",
            "读取指定相对文件路径的内容。适用于需要查看文件内容的场景，支持查看各种类型文件的完整内容。请注意，此工具仅用于文件，不可用于目录。",
            createInputSchema(
                    Map.of("path", createProperty("string", "工作目录中文件的相对路径。")),
                    List.of("path")
            ),
            ReadFileInput.class,
            Tools::readFile
    );

    public static ToolDefinition WriteFileDefinition = new ToolDefinition(
            "write_file",
            "将内容写入文件。如果文件不存在，则创建该文件。",
            createInputSchema(
                    Map.of(
                            "path", createProperty("string", "文件的路径"),
                            "content", createProperty("string", "要写入的内容")
                    ),
                    List.of("path", "content")
            ),
            WriteFileInput.class,
            Tools::writeFile
    );

    /**
     * Bash 命令执行工具定义
     * <p>
     * 执行 Shell 命令并返回输出结果。支持多种命令类型，适用于文件操作、程序执行、系统信息查询等场景。
     * </p>
     */
    public static ToolDefinition BashDefinition = new ToolDefinition(
            "bash",
            "执行 Shell 命令并返回其输出结果。适用于需要运行各种 Shell 命令的场景，可用于文件操作、程序执行、系统信息查询等多种任务。比如：如果用户让你打开网站，直接使用 start [url]；你要进入目录，如果返回系统找不到指定的路径，则尝试一下绝对路径。始终记住，你处于 " + System.getProperty("os.name").toLowerCase() + " 操作系统。",
            createInputSchema(
                    Map.of(
                            "command", createProperty("string", "要执行的命令字符串。"),
                            "type", createProperty("string", "命令类型，可选值：cmd（Windows CMD）、powershell（Windows PowerShell）、bash（Linux/Mac）。如不指定，系统将根据操作系统自动选择。")
                    ),
                    List.of("command","type")
            ),
            BashInput.class,
            Tools::executeBash
    );

    public static ToolDefinition EditFileDefinition = new ToolDefinition(
            "edit_file",
            "编辑文本文件。\n\n将指定文件中的'oldStr'替换为'newStr'。请注意，'oldStr'和'newStr'必须不同。\n若指定路径的文件不存在，则会自动创建该文件。",
            createInputSchema(
                    Map.of(
                            "path", createProperty("string", "文件的路径"),
                            "oldStr", createProperty("string", "要搜索的文本\n必须完全匹配，并且只能有一个完全匹配"),
                            "newStr", createProperty("string", "替换oldStr的文本")
                    ),
                    List.of()
            ),
            EditFileInput.class,
            Tools::editFile
    );

    public static ToolDefinition ListFilesDefinition = new ToolDefinition(
            "list_files",
            "列出指定路径下的文件和目录，支持按文件类型筛选。若未指定路径，则默认列出当前目录的内容。",
            createInputSchema(
                    Map.of(
                            "path", createProperty("string", "可选的相对路径，用于列出文件。若未提供，则默认为当前目录。"),
                            "fileType", createProperty("string", "可选的文件扩展名，用于限制搜索范围（例如：'md'、'java'、'txt'）。")
                    ),
                    List.of()
            ),
            ListFilesInput.class,
            Tools::listFiles
    );

    public static ToolDefinition ContentSearchDefinition = new ToolDefinition(
            "content_search",
            "使用ripgrep (rg)搜索代码或文本。\n\n适用于查找代码库中的代码片段、函数定义、变量使用情况或任何文本内容。\n支持按正则表达式、文件类型或目录进行精准搜索。",
            createInputSchema(
                    Map.of(
                            "pattern", createProperty("string", "要查找的文本内容或内容的正则表达式。"),
                            "path", createProperty("string", "可选搜索路径（文件或目录）。"),
                            "fileType", createProperty("string", "可选的文件扩展名，用于限制搜索范围（例如，'md'、'java'、'txt'）。"),
                            "caseSensitive", createProperty("boolean", "搜索是否应区分大小写（默认值：false）。")
                    ),
                    List.of("pattern")
            ),
            ContentSearchInput.class,
            Tools::searchContent
    );

    /**
     * 待办事项工具定义
     * 用于管理任务列表，支持添加、更新或完成任务
     */
    public static ToolDefinition TodoDefinition = new ToolDefinition(
            "todo",
            "管理待办事项列表。使用此工具可以添加、更新或完成任务。支持任务状态追踪，包括待处理、进行中和已完成三种状态。",
            createInputSchema(
                    Map.of("todos",
                            Map.of(
                                    "type", "array",
                                    "description", "待办事项列表",
                                    "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "id", Map.of("type", "string", "description", "任务的唯一标识符"),
                                                    "content", Map.of("type", "string", "description", "待办事项的内容描述"),
                                                    "status", Map.of(
                                                            "type", "string",
                                                            "enum", List.of("pending", "in_progress", "completed"),
                                                            "description", "任务状态：pending-待处理, in_progress-进行中, completed-已完成"
                                                    )
                                            ),
                                            "required", List.of("id", "content", "status")
                                    )
                            )
                    ),
                    List.of("todos")
            ),
            TodoInput.class,
            Agent03.todoManager::updateTodos
    );

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
}
