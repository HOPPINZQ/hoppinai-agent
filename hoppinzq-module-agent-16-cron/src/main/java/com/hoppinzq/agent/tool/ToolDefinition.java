package com.hoppinzq.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * ToolDefinition 工具定义类（cron 模块本地副本）。
 * <p>
 * 与 module-02 的差异：新增 3 个 cron 工具定义（schedule_cron / list_crons / cancel_cron）。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinition {
    private String name;
    private String description;
    private Tool.InputSchema inputSchema;

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

    public static Tool.InputSchema createInputSchema(Map<String, Object> properties, List<String> required) {
        ObjectNode propertiesNode = OBJECT_MAPPER.valueToTree(properties);

        Tool.InputSchema.Builder schemaBuilder = Tool.InputSchema.builder()
                .properties(JsonValue.fromJsonNode(propertiesNode));

        if (required != null && !required.isEmpty()) {
            schemaBuilder.required(required);
        }

        return schemaBuilder.build();
    }

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

public static ToolDefinition GlobDefinition = new ToolDefinition(
            "glob",
            "使用glob模式查找匹配的文件和目录。\n\n支持通配符匹配文件路径，类似Python的glob.glob()功能。\n支持的glob模式：\n- *: 匹配当前目录下所有文件和目录\n- *.ext: 匹配当前目录下所有.ext文件\n- **/*.ext: 递归匹配所有.ext文件\n- test_*.py: 匹配以test_开头的Python文件\n- */: 只匹配目录",
            createInputSchema(
                    Map.of(
                            "pattern", createProperty("string", "Glob匹配模式，支持通配符。例如：*.java, **/*.json, test_*.py")
                    ),
                    List.of("pattern")
            ),
            GlobInput.class,
            Tools::glob
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

    // ========================= cron 工具 =========================

    public static ToolDefinition ScheduleCronDefinition = new ToolDefinition(
            "schedule_cron",
            "调度一个定时任务。命中 5 字段 cron 表达式（min hour day-of-month month day-of-week）时，把 prompt 作为新的用户消息注入 agent。\n" +
                    "示例：\n" +
                    "- 每 2 分钟跑一次：*/2 * * * *\n" +
                    "- 每天 9:30：30 9 * * *\n" +
                    "- 每周一 9 点：0 9 * * 1\n" +
                    "recurring=false 表示只触发一次（one-shot）；durable=true 表示进程重启后仍然有效。",
            createInputSchema(
                    Map.of(
                            "cron", createProperty("string", "5 字段 cron 表达式，例如 '*/2 * * * *'."),
                            "prompt", createProperty("string", "触发时要注入的 prompt。"),
                            "recurring", createProperty("boolean", "是否周期触发，默认 true。"),
                            "durable", createProperty("boolean", "是否持久化到 .scheduled_tasks.json，默认 false。")
                    ),
                    List.of("cron", "prompt")
            ),
            CronScheduleInput.class,
            Tools::scheduleCron
    );

    public static ToolDefinition ListCronsDefinition = new ToolDefinition(
            "list_crons",
            "列出当前所有已注册的 cron 任务（id、cron、prompt、recurring、durable、lastFire、nextFire）。无需参数。",
            createInputSchema(
                    Map.of(),
                    List.of()
            ),
            Map.class,
            Tools::listCrons
    );

    public static ToolDefinition CancelCronDefinition = new ToolDefinition(
            "cancel_cron",
            "按 id 取消一个已注册的 cron 任务。",
            createInputSchema(
                    Map.of("id", createProperty("string", "要取消的任务 id（来自 list_crons）")),
                    List.of("id")
            ),
            CronCancelInput.class,
            Tools::cancelCron
    );

    public static Map<String, Object> createProperty(String type, String description) {
        Map<String, Object> property = new HashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
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
        throw new IllegalStateException("没有该工具的处理方法: " + name);
    }
}
