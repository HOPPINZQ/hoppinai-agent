package com.hoppinzq.agent.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.Agent08;
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
    private String description;
    private Tool.InputSchema inputSchema;
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
                            "type", createProperty("string", "命令类型，可选值：cmd（Windows CMD）、powershell（Windows PowerShell）、bash（Linux/Mac）。如不指定，系统将根据操作系统自动选择。"),
                            "run_in_background", createProperty("boolean", "是否在后台运行（可选）。设置为 true 时，命令将在后台线程中执行，立即返回任务ID，不阻塞主线程。适用于耗时命令如 npm install、pytest、docker build等。")
                    ),
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
    public static ToolDefinition SubAgentDefinition = new ToolDefinition(
            "sub_agent",
            "将子任务委托给子智能体处理。子智能体拥有读取文件、写入文件、编辑文件和搜索内容等工具能力，专门用于处理具体的文件操作任务。",
            createInputSchema(
                    Map.of("prompt", createProperty("string", "给子智能体的任务提示词，详细描述需要完成的任务")),
                    List.of("prompt")
            ),
            SubAgentInput.class,
            Tools::executeSubAgent
    );
    public static ToolDefinition SkillsDefinition = new ToolDefinition(
            "load_skill",
            "加载指定的技能（工具集合）。可用技能：" + String.join(", ", Agent08.skillLoader.getAvailableSkills()),
            createInputSchema(
                    Map.of("skill_name", createProperty("string", "要加载的技能名称")),
                    List.of("skill_name")
            ),
            LoadSkillInput.class,
            Tools::skillLoad
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
            Agent08.todoManager::updateTodos
    );
    /**
     * 任务创建工具定义
     * <p>
     * 用于创建新任务，将大型目标分解为更小的可执行任务单元。
     * 支持为任务设置标题、详细描述和依赖关系，便于后续跟踪和管理。
     * </p>
     */
    public static ToolDefinition TaskCreateDefinition = new ToolDefinition(
            "task_create",
            "创建新任务。使用此工具将大型目标分解为更小的、可管理的任务单元。\n\n" +
                    "使用场景：\n" +
                    "• 需要完成复杂的多步骤任务时，将其拆分为子任务\n" +
                    "• 需要跟踪任务进度时，创建结构化的任务列表\n" +
                    "• 多个任务需要协同完成时，建立清晰的任务层级\n\n" +
                    "参数说明：\n" +
                    "• subject: 任务的简短标题或名称（必填）\n" +
                    "• description: 任务的详细描述，包括具体要求、验收标准等（可选）\n" +
                    "• blockedBy: 此任务依赖的前置任务ID列表（可选）",
            createInputSchema(
                    Map.of(
                            "subject", createProperty("string", "任务的标题或简短描述，概括任务的核心内容"),
                            "description", createProperty("string", "任务的详细说明，包括具体要求、实现思路、验收标准等信息（可选）"),
                            "blockedBy", Map.of(
                                    "type", "array",
                                    "items", Map.of("type", "string"),
                                    "description", "此任务依赖的前置任务ID列表。这些任务完成后，当前任务才能开始执行。支持短ID前缀匹配。"
                            )
                    ),
                    List.of("subject")
            ),
            TaskCreateInput.class,
            Tools::createTask
    );
    /**
     * 任务更新工具定义
     * <p>
     * 用于更新任务的状态或依赖关系。当任务完成时，会自动解除依赖该任务的其他任务的阻塞状态。
     * 支持设置任务状态（pending、in_progress、completed）和建立任务间的依赖关系。
     * </p>
     */
    public static ToolDefinition TaskUpdateDefinition = new ToolDefinition(
            "task_update",
            "更新任务的状态或依赖关系。完成任务时会自动解除依赖该任务的其他任务的阻塞状态。\n\n" +
                    "使用场景：\n" +
                    "• 开始执行任务时，将状态更新为 in_progress\n" +
                    "• 完成任务时，将状态更新为 completed（会自动解除依赖）\n" +
                    "• 需要建立任务依赖时，使用 addBlockedBy 添加前置任务\n" +
                    "• 需要设置后续任务时，使用 addBlocks 添加后置任务\n\n" +
                    "状态说明：\n" +
                    "• pending: 待处理，任务尚未开始\n" +
                    "• in_progress: 进行中，任务正在执行\n" +
                    "• completed: 已完成，任务执行完毕\n\n" +
                    "依赖说明：\n" +
                    "• addBlockedBy: 添加此任务依赖的前置任务ID列表（这些任务完成后此任务才能开始）\n" +
                    "• addBlocks: 添加依赖于此任务的后置任务ID列表（此任务完成后这些任务才能开始）",
            createInputSchema(
                    Map.of(
                            "taskId", createProperty("string", "要更新的任务ID，支持短ID前缀匹配"),
                            "status", createProperty("string", "任务的新状态：pending（待处理）、in_progress（进行中）或 completed（已完成）"),
                            "addBlockedBy", Map.of(
                                    "type", "array",
                                    "items", Map.of("type", "string"),
                                    "description", "添加此任务依赖的前置任务ID列表。这些任务完成后，当前任务才能开始执行。"
                            ),
                            "addBlocks", Map.of(
                                    "type", "array",
                                    "items", Map.of("type", "string"),
                                    "description", "添加依赖于此任务的后置任务ID列表。当前任务完成后，这些任务才能开始执行。"
                            )
                    ),
                    List.of("taskId")
            ),
            TaskUpdateInput.class,
            Tools::updateTask
    );
    /**
     * 任务列表工具定义
     * <p>
     * 用于列出所有任务及其状态和依赖关系。提供任务清单概览，便于了解整体进度和任务间的关系。
     * </p>
     */
    public static ToolDefinition TaskListDefinition = new ToolDefinition(
            "task_list",
            "列出所有任务及其状态和依赖关系。\n\n" +
                    "使用场景：\n" +
                    "• 需要查看当前所有任务的概览时\n" +
                    "• 需要了解任务进度和状态时\n" +
                    "• 需要查看任务间的依赖关系时\n" +
                    "• 需要确定哪些任务可以开始执行时\n\n" +
                    "输出信息：\n" +
                    "• 任务ID和标题\n" +
                    "• 任务状态（pending/in_progress/completed）\n" +
                    "• 依赖关系（blockedBy/blocks）",
            createInputSchema(Map.of(), List.of()),
            TaskListInput.class,
            Tools::listTasks
    );
    /**
     * 任务详情工具定义
     * <p>
     * 用于获取指定任务的完整详细信息，包括描述、状态、依赖关系等。
     * </p>
     */
    public static ToolDefinition TaskGetDefinition = new ToolDefinition(
            "task_get",
            "获取指定任务的完整详细信息。\n\n" +
                    "使用场景：\n" +
                    "• 需要查看任务的详细描述时\n" +
                    "• 需要了解任务的具体状态时\n" +
                    "• 需要查看任务的依赖关系详情时\n" +
                    "• 需要确认任务是否被阻塞时\n\n" +
                    "输出信息：\n" +
                    "• 任务ID、标题和详细描述\n" +
                    "• 当前状态\n" +
                    "• 依赖关系列表（blockedBy和blocks）",
            createInputSchema(
                    Map.of("taskId", createProperty("string", "要查询详情的任务ID，支持短ID前缀匹配")),
                    List.of("taskId")
            ),
            TaskGetInput.class,
            Tools::getTask
    );

    /**
     * 任务认领工具定义
     * <p>
     * 用于认领一个待处理的任务，设置所有者并变更状态为进行中。
     * 会自动检查任务状态和依赖关系，确保任务可以开始执行。
     * </p>
     */
    public static ToolDefinition TaskClaimDefinition = new ToolDefinition(
            "task_claim",
            "认领一个待处理的任务。设置所有者并变更状态为进行中（in_progress）。\n\n" +
                    "使用场景：\n" +
                    "• 开始执行一个待处理的任务时\n" +
                    "• 多个代理协作时，认领并锁定一个任务\n" +
                    "• 确保任务依赖都已满足后再开始执行\n\n" +
                    "功能说明：\n" +
                    "• 只能认领状态为 pending 的任务\n" +
                    "• 自动检查所有 blockedBy 依赖是否已完成\n" +
                    "• 如果依赖未完成，会返回阻塞信息\n" +
                    "• 认领成功后设置 owner 并变更状态为 in_progress\n\n" +
                    "参数说明：\n" +
                    "• taskId: 要认领的任务ID（必填）\n" +
                    "• owner: 认领者名称（可选，默认为 'agent'）",
            createInputSchema(
                    Map.of(
                            "taskId", createProperty("string", "要认领的任务ID，支持短ID前缀匹配"),
                            "owner", createProperty("string", "认领者名称（可选，默认为 'agent'）")
                    ),
                    List.of("taskId")
            ),
            TaskClaimInput.class,
            Tools::claimTask
    );

    /**
     * 任务完成工具定义
     * <p>
     * 用于标记一个进行中的任务为已完成，并自动解除依赖该任务的其他任务的阻塞状态。
     * </p>
     */
    public static ToolDefinition TaskCompleteDefinition = new ToolDefinition(
            "task_complete",
            "完成一个进行中的任务。标记为已完成并解除依赖该任务的其他任务的阻塞状态。\n\n" +
                    "使用场景：\n" +
                    "• 任务执行完毕，标记为完成状态\n" +
                    "• 需要自动解除下游任务的阻塞时\n" +
                    "• 查看哪些任务因本任务的完成而被解锁\n\n" +
                    "功能说明：\n" +
                    "• 只能完成状态为 in_progress 的任务\n" +
                    "• 完成后自动清除依赖关系（移除此任务对其他任务的阻塞）\n" +
                    "• 返回被解除阻塞的下游任务列表\n" +
                    "• 便于了解哪些任务现在可以开始执行\n\n" +
                    "参数说明：\n" +
                    "• taskId: 要完成的任务ID（必填）",
            createInputSchema(
                    Map.of("taskId", createProperty("string", "要完成的任务ID，支持短ID前缀匹配")),
                    List.of("taskId")
            ),
            TaskCompleteInput.class,
            Tools::completeTask
    );

    /**
     * 后台任务执行工具定义
     * <p>
     * 在后台线程中执行命令，立即返回task_id，不阻塞主线程。
     * 适用于耗时命令如 npm install、pytest、docker build等。
     * </p>
     */
    public static ToolDefinition BackgroundRunDefinition = new ToolDefinition(
            "background_run",
            "在后台线程中执行命令。立即返回task_id，不阻塞主线程。适用于耗时命令如 npm install、pytest、docker build等。",
            createInputSchema(
                    Map.of("command", ToolDefinition.createProperty("string", "要在后台执行的 bash 命令。")),
                    List.of("command")
            ),
            BackgroundRunInput.class,
            Tools::backgroundRun
    );
    /**
     * 后台任务状态检查工具定义
     * <p>
     * 检查后台任务状态。省略task_id参数可列出所有任务。
     * </p>
     */
    public static ToolDefinition CheckBackgroundDefinition = new ToolDefinition(
            "check_background",
            "检查后台任务状态。省略task_id参数可列出所有任务。",
            createInputSchema(
                    Map.of("taskId", ToolDefinition.createProperty("string", "要检查的任务ID（可选）")),
                    List.of()
            ),
            CheckBackgroundInput.class,
            Tools::checkBackground
    );
    // toolCall 或者 MCP需要用的三个字段
    private String name;
    // 工具的参数类型和处理函数
    private Class<?> type;

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
        throw new IllegalStateException("没有该工具的处理方法: " + name);
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
     * 创建InputSchema
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
