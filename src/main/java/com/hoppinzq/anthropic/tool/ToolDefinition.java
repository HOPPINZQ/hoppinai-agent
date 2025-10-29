package com.hoppinzq.anthropic.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.anthropic.tool.schema.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hoppinzq.anthropic.constant.AIConstants.OBJECT_MAPPER;

/**
 * ToolDefinition 工具定义类
 * @author hoppinzq
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ToolDefinition {
    // toolCall 或者 MCP需要用的三个字段
    private String name;
    private String description;
    private Tool.InputSchema inputSchema;

    // 工具的参数类型和处理函数
    private Class type;
    private Function<String, String> function;

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

    public static ToolDefinition BashDefinition = new ToolDefinition(
            "bash",
            "执行Bash命令并返回其输出结果。适用于需要运行各种Shell命令的场景，可用于文件操作、程序执行、系统信息查询等多种任务。比如：如果用户让你打开网站，直接使用start [url]",
            createInputSchema(
                    Map.of("command", createProperty("string", "要执行的bash命令。")),
                    List.of("command")
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

    public static ToolDefinition WebSearchDefinition = new ToolDefinition(
            "web_search",
            "搜索网络上的内容以获取实时信息\n\n获取最新技术信息、文档、API更新等\n使用场景：查找最新技术资料、解决技术问题。\n应谨慎使用，避免频繁搜索导致用户体验不佳和成本过高",
            createInputSchema(
                    Map.of("query", createProperty("string", "要搜索的内容")),
                    List.of("query")
            ),
            WebSearchInput.class,
            Tools::searchWeb
    );

    /**
     * 创建属性定义
     */
    private static Map<String, Object> createProperty(String type, String description) {
        Map<String, Object> property = new HashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    /**
     * 创建 InputSchema
     */
    private static Tool.InputSchema createInputSchema(Map<String, Object> properties, List<String> required) {
        ObjectNode inputSchema = OBJECT_MAPPER.valueToTree(properties);
        if (!required.isEmpty()) {
            inputSchema.putIfAbsent("required", OBJECT_MAPPER.valueToTree(required));
        }
        return Tool.InputSchema.builder()
                .properties(JsonValue.fromJsonNode(inputSchema))
                .build();
    }
}