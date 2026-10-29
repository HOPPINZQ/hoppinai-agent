package com.hoppinzq.agent.capability.task;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

import static com.hoppinzq.agent.tool.ToolDefinition.createInputSchema;
import static com.hoppinzq.agent.tool.ToolDefinition.createProperty;

/**
 * 任务系统能力（可整体复制的能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>6 个工具：{@code task_create / task_update / task_list / task_get / task_claim / task_complete}</li>
 *   <li>1 个命令：{@code /tasks}（打印当前任务图）</li>
 *   <li>系统提示片段：任务图结构与状态流转使用指南</li>
 * </ul>
 *
 * <p>能力自持 {@link TaskManager}：任务持久化在 {@code <ROOT>/.tasks/}（能力自有的磁盘存储，
 * 不进内核 SessionData），拔除本目录并删除装配行即可完全移除该能力。
 * 原先经 {@code ToolDefinition} 静态字段 + {@code Agent07.taskManager} 静态引用的接线
 * 已全部收拢到本类，工具执行体与 manager 同包后直接调用，不再有任何静态回调。
 *
 * <p>对内核的依赖仅限：Capability 契约、tool/ToolDefinition、SessionManager（会话 ID）、
 * AIConstants.ROOT（工作目录）。
 *
 * @author hoppinzq
 */
public class TaskCapability implements Capability {

    /** 能力自持的任务管理器（工具与命令都经它读写任务图） */
    private final TaskManager taskManager;

    /** onStart 时捕获的宿主 Agent（当前仅用于诊断输出，预留后续挂载点扩展） */
    private ZQAgent agent;

    public TaskCapability(SessionManager sessionManager) {
        this.taskManager = new TaskManager(
                sessionManager == null ? "default" : sessionManager.getSessionId());
    }

    @Override
    public String name() {
        return "task";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                createDefinition(),
                updateDefinition(),
                listDefinition(),
                getDefinition(),
                claimDefinition(),
                completeDefinition());
    }

    /**
     * task_create：把大型目标分解为更小的可执行任务单元，可声明前置依赖。
     */
    private ToolDefinition createDefinition() {
        return new ToolDefinition(
                "task_create",
                "创建新任务。使用此工具将大型目标分解为更小的、可管理的任务单元。\n\n" +
                        "使用场景：\n" +
                        "• 需要完成复杂的多步骤任务时，将其拆分为子任务\n" +
                        "• 需要跟踪任务进度时，创建结构化的任务列表\n" +
                        "• 多个任务需要协同完成时，建立清晰的任务层级\n\n" +
                        "参数说明：\n" +
                        "• subject: 任务的简短标题或名称（必填）\n" +
                        "• description: 任务的详细描述，包括具体要求、验收标准等（可选）\n" +
                        "• blockedBy: 此任务依赖的前置任务ID列表（可选，支持短ID前缀匹配）",
                createInputSchema(
                        Map.of(
                                "subject", createProperty("string", "任务的标题或简短描述，概括任务的核心内容"),
                                "description", createProperty("string", "任务的详细说明，包括具体要求、实现思路、验收标准等信息（可选）"),
                                "blockedBy", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "description", "此任务依赖的前置任务ID列表（支持短ID，如 'a1b2c3d4'）。这些任务完成后，当前任务才能开始执行。"
                                )
                        ),
                        List.of("subject")
                ),
                TaskCreateInput.class,
                this::invokeCreate);
    }

    /**
     * task_update：更新状态或依赖关系；完成任务时自动解除下游阻塞。
     */
    private ToolDefinition updateDefinition() {
        return new ToolDefinition(
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
                        "• completed: 已完成，任务执行完毕",
                createInputSchema(
                        Map.of(
                                "taskId", createProperty("string", "要更新的任务ID（支持短ID前缀匹配）"),
                                "status", createProperty("string", "任务的新状态：pending（待处理）、in_progress（进行中）或 completed（已完成）"),
                                "addBlockedBy", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "description", "添加此任务依赖的前置任务ID列表（支持短ID）。这些任务完成后，当前任务才能开始执行。"
                                ),
                                "addBlocks", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "description", "添加依赖于此任务的后置任务ID列表（支持短ID）。当前任务完成后，这些任务才能开始执行。"
                                )
                        ),
                        List.of("taskId")
                ),
                TaskUpdateInput.class,
                this::invokeUpdate);
    }

    /**
     * task_list：任务全景（状态统计 + 可立即执行的任务）。
     */
    private ToolDefinition listDefinition() {
        return new ToolDefinition(
                "task_list",
                "列出所有任务及其状态和依赖关系。\n\n" +
                        "使用场景：\n" +
                        "• 需要查看当前所有任务的概览时\n" +
                        "• 需要了解任务进度和状态时\n" +
                        "• 需要查看任务间的依赖关系时\n" +
                        "• 需要确定哪些任务可以开始执行时",
                createInputSchema(Map.of(), List.of()),
                TaskListInput.class,
                this::invokeList);
    }

    /**
     * task_get：单个任务的完整详情（描述 + 双向依赖）。
     */
    private ToolDefinition getDefinition() {
        return new ToolDefinition(
                "task_get",
                "获取指定任务的完整详细信息。\n\n" +
                        "使用场景：\n" +
                        "• 需要查看任务的详细描述时\n" +
                        "• 需要了解任务的具体状态时\n" +
                        "• 需要查看任务的依赖关系详情时\n" +
                        "• 需要确认任务是否被阻塞时",
                createInputSchema(
                        Map.of("taskId", createProperty("string", "要查询详情的任务ID（支持短ID前缀匹配）")),
                        List.of("taskId")
                ),
                TaskGetInput.class,
                this::invokeGet);
    }

    /**
     * task_claim：认领待处理任务（检查依赖后置为 in_progress）。
     */
    private ToolDefinition claimDefinition() {
        return new ToolDefinition(
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
                        "• 认领成功后设置 owner 并变更状态为 in_progress",
                createInputSchema(
                        Map.of(
                                "taskId", createProperty("string", "要认领的任务ID（支持短ID前缀匹配）"),
                                "owner", createProperty("string", "认领者名称（可选，默认为 'agent'）")
                        ),
                        List.of("taskId")
                ),
                TaskClaimInput.class,
                this::invokeClaim);
    }

    /**
     * task_complete：完成任务并返回被解除阻塞的下游任务。
     */
    private ToolDefinition completeDefinition() {
        return new ToolDefinition(
                "task_complete",
                "完成一个进行中的任务。标记为已完成并解除依赖该任务的其他任务的阻塞状态。\n\n" +
                        "使用场景：\n" +
                        "• 任务执行完毕，标记为完成状态\n" +
                        "• 需要自动解除下游任务的阻塞时\n" +
                        "• 查看哪些任务因本任务的完成而被解锁",
                createInputSchema(
                        Map.of("taskId", createProperty("string", "要完成的任务ID（支持短ID前缀匹配）")),
                        List.of("taskId")
                ),
                TaskCompleteInput.class,
                this::invokeComplete);
    }

    // ============================== 工具执行体 ==============================
    // 工具与 TaskManager 同包，直接方法调用；不再经过任何静态字段或回调钩子。

    private String invokeCreate(TaskCreateInput input) {
        try {
            return taskManager.createTask(input.getSubject(), input.getDescription(), input.getBlockedBy());
        } catch (Exception e) {
            return "创建任务失败: " + e.getMessage();
        }
    }

    private String invokeUpdate(TaskUpdateInput input) {
        try {
            return taskManager.updateTask(input.getTaskId(), input.getStatus(),
                    input.getAddBlockedBy(), input.getAddBlocks());
        } catch (Exception e) {
            return "更新任务失败: " + e.getMessage();
        }
    }

    private String invokeList(TaskListInput input) {
        try {
            return taskManager.listAllTasks();
        } catch (Exception e) {
            return "列出任务失败: " + e.getMessage();
        }
    }

    private String invokeGet(TaskGetInput input) {
        try {
            return taskManager.getTask(input.getTaskId());
        } catch (Exception e) {
            return "获取任务失败: " + e.getMessage();
        }
    }

    private String invokeClaim(TaskClaimInput input) {
        try {
            return taskManager.claimTask(input.getTaskId(), input.getOwner());
        } catch (Exception e) {
            return "认领任务失败: " + e.getMessage();
        }
    }

    private String invokeComplete(TaskCompleteInput input) {
        try {
            return taskManager.completeTask(input.getTaskId());
        } catch (Exception e) {
            return "完成任务失败: " + e.getMessage();
        }
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/tasks";
            }

            @Override
            public String description() {
                return "查看当前任务图（状态与依赖）";
            }

            @Override
            public void execute() {
                System.out.println(taskManager.listAllTasks());
            }
        });
    }

    // ============================== 提示词片段 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of("""
                ## 任务管理系统

                任务图结构示例：
                ```
                task_1 (completed)
                   ↓
                task_2 ←→ task_4
                   ↓
                task_3 ←┘
                ```

                ### 任务状态流转
                - **pending**：任务待执行，可能被其他任务阻塞
                - **in_progress**：任务正在执行中
                - **completed**：任务已完成，自动解除阻塞其他任务

                ### 任务工具使用指南
                1. **task_create** - 创建新任务：接收新任务、发现子任务、识别后续工作时使用；
                   用 addBlockedBy 设置前置任务
                2. **task_update** - 更新状态：pending → in_progress → completed；
                   完成任务时自动解除依赖该任务的其他任务的阻塞
                3. **task_list** - 查看任务全景：开始工作前、完成任务后、迷失方向时使用；
                   重点关注 status、blockedBy、owner
                4. **task_get** - 获取任务详情：确认任务要求、检查依赖状态

                ### 任务执行策略
                - **优先级规则**：blockedBy 为空的任务优先执行
                - **并行处理**：无依赖关系的任务可以并行处理
                - **依赖跟踪**：完成任务后检查哪些任务被解除阻塞
                - **进度汇报**：每完成一个任务就调用 task_list 查看整体进度

                ## 任务工作流程
                1. 接收任务 → task_create 分解任务
                2. task_list 检查待办 → 确认可执行任务（无阻塞）
                3. task_get 查看详情 → 理解任务要求
                4. task_update(in_progress) → 开始执行
                5. 执行具体工作 → 使用工具完成任务
                6. task_update(completed) → 标记完成
                7. 返回步骤2 → 处理下一个任务""");
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "任务持久化目录: <ROOT>/" + TaskConfig.TASKS_DIR_NAME + "\n" +
                "已有任务数: " + taskManager.getTaskCount();
    }
}
