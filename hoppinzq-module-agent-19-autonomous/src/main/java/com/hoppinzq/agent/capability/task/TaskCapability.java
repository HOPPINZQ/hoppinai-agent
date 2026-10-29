package com.hoppinzq.agent.capability.task;

import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.capability.teams.TeammateContext;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务板能力（可整体复制的能力包）。
 *
 * <p>向内核提供 5 个工具：{@code create_task} / {@code list_tasks} / {@code get_task} /
 * {@code claim_task} / {@code complete_task}，全部委托给本能力自持的 {@link TaskManager}。
 *
 * <p>认领者身份取自 teams 能力的 {@link TeammateContext}（teammate 线程的 ThreadLocal），
 * 因此 {@code claim_task} 只能在 teammate 线程里调用 —— 这是自主认领的语义前提。
 *
 * <p>对内核的依赖仅限：Capability 契约、ToolDefinition。
 * 任务板状态由 TaskManager 自己持久化，不进内核会话文件。
 *
 * @author hoppinzq
 */
public class TaskCapability implements Capability {

    private final TaskManager taskManager = new TaskManager();

    /** 供其他能力（如 autonomous）共享同一块任务板 */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    @Override
    public String name() {
        return "task";
    }

    @Override
    public List<ToolDefinition> tools() {
        List<ToolDefinition> tools = new ArrayList<>();

        tools.add(new ToolDefinition(
                "create_task",
                "创建一个新任务。任务会被 teammates 的自主认领机制（空闲轮询）自动发现并认领。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "subject", ToolDefinition.createProperty("string", "任务标题。"),
                                "description", ToolDefinition.createProperty("string", "任务描述。")),
                        List.of("subject")),
                TaskCreateInput.class,
                this::createTask));

        tools.add(new ToolDefinition(
                "list_tasks",
                "列出所有任务（含状态和依赖）。",
                ToolDefinition.createInputSchema(Map.of(), List.of()),
                Map.class,
                this::listTasks));

        tools.add(new ToolDefinition(
                "get_task",
                "获取单个任务详情。",
                ToolDefinition.createInputSchema(
                        Map.of("taskId", ToolDefinition.createProperty("integer", "任务 id。")),
                        List.of("taskId")),
                TaskGetInput.class,
                this::getTask));

        tools.add(new ToolDefinition(
                "claim_task",
                "认领一个任务（设置 owner + in_progress 状态）。仅 teammate 可调用。",
                ToolDefinition.createInputSchema(
                        Map.of("taskId", ToolDefinition.createProperty("integer", "要认领的任务 id。")),
                        List.of("taskId")),
                TaskClaimInput.class,
                this::claimTask));

        tools.add(new ToolDefinition(
                "complete_task",
                "将任务标记为完成。",
                ToolDefinition.createInputSchema(
                        Map.of("taskId", ToolDefinition.createProperty("integer", "要完成的任务 id。")),
                        List.of("taskId")),
                TaskClaimInput.class,
                this::completeTask));

        return tools;
    }

    @Override
    public List<String> promptFragments() {
        return List.of("""
                ## 任务板
                - create_task(subject, description)：把工作拆成任务板条目，teammate 会自动认领
                - list_tasks() / get_task(taskId)：查看任务状态与依赖
                - claim_task(taskId)：仅 teammate 线程可用（认领人自动取当前 teammate 名）
                - complete_task(taskId)：标记完成""");
    }

    public String description() {
        return "任务板：create/list/get/claim/complete 五个工具，状态由 TaskManager 自持";
    }

    // ============================== 工具实现 ==============================

    private String createTask(TaskCreateInput in) {
        try {
            if (in == null || in.getSubject() == null || in.getSubject().isBlank()) {
                return "错误：subject 不能为空";
            }
            return taskManager.createTask(in.getSubject(), in.getDescription());
        } catch (Exception e) {
            return "create_task 错误: " + e.getMessage();
        }
    }

    private String listTasks(Map<?, ?> ignored) {
        try {
            return taskManager.listAllTasks();
        } catch (Exception e) {
            return "list_tasks 错误: " + e.getMessage();
        }
    }

    private String getTask(TaskGetInput in) {
        try {
            if (in == null || in.getTaskId() == null) {
                return "错误：taskId 不能为空";
            }
            return taskManager.getTask(in.getTaskId());
        } catch (Exception e) {
            return "get_task 错误: " + e.getMessage();
        }
    }

    private String claimTask(TaskClaimInput in) {
        try {
            if (in == null || in.getTaskId() == null) {
                return "错误：taskId 不能为空";
            }
            String owner = TeammateContext.current();
            if (owner == null || owner.isEmpty()) {
                return "错误：claim_task 只能由 teammate 调用";
            }
            return taskManager.claimTask(in.getTaskId(), owner);
        } catch (Exception e) {
            return "claim_task 错误: " + e.getMessage();
        }
    }

    private String completeTask(TaskClaimInput in) {
        try {
            if (in == null || in.getTaskId() == null) {
                return "错误：taskId 不能为空";
            }
            return taskManager.completeTask(in.getTaskId());
        } catch (Exception e) {
            return "complete_task 错误: " + e.getMessage();
        }
    }
}