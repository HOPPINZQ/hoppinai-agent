package com.hoppinzq.agent.capability.worktree;

import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.capability.task.TaskManager;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * git worktree 隔离能力（可整体复制的能力包）。
 *
 * <p>向内核提供 3 个工具：{@code create_worktree} / {@code keep_worktree} / {@code remove_worktree}。
 * 每个 worktree 是 {@code .worktrees/<name>} 下的一份独立工作区（分支 {@code wt/<name>}），
 * 并可通过 taskId 绑定到任务板上的一张任务。
 *
 * <p>与任务板的联动：{@link WorktreeManager} 绑定 task → worktree 后，
 * teammate 认领该任务时由 {@link WorktreeContext} 把当前线程 cwd 切到对应目录
 * （切换动作在 teammate 侧发生，本能力只负责创建/查询/清理）。
 *
 * <p>对内核的依赖仅限：Capability 契约、ToolDefinition；对同批能力包依赖：task（TaskManager）。
 *
 * @author hoppinzq
 */
public class WorktreeCapability implements Capability {

    private final WorktreeManager worktreeManager;

    public WorktreeCapability(TaskManager taskManager) {
        this.worktreeManager = new WorktreeManager(taskManager);
    }

    /** 供装配方或 teammate 侧查询 worktree 状态 */
    public WorktreeManager getWorktreeManager() {
        return worktreeManager;
    }

    @Override
    public String name() {
        return "worktree";
    }

    @Override
    public List<ToolDefinition> tools() {
        ToolDefinition create = new ToolDefinition(
                "create_worktree",
                "创建一个 git worktree（分支 wt/<name> 挂到 .worktrees/<name> 目录），并可选绑定到某个 task。"
                        + "teammate 认领带 worktree 的 task 后会自动把 cwd 切到该目录。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "name", ToolDefinition.createProperty("string", "worktree 名字（也是分支名 wt/<name>）"),
                                "taskId", ToolDefinition.createProperty("integer", "可选：要绑定的 task id")),
                        List.of("name")),
                CreateWorktreeInput.class,
                this::createWorktree);

        ToolDefinition remove = new ToolDefinition(
                "remove_worktree",
                "移除一个 worktree。discard=true（默认）时连同分支一起删除；discard=false 仅从 git worktree 索引中移除。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "name", ToolDefinition.createProperty("string", "worktree 名字"),
                                "discard", ToolDefinition.createProperty("boolean", "是否连同分支删除，默认 true")),
                        List.of("name")),
                RemoveWorktreeInput.class,
                this::removeWorktree);

        ToolDefinition keep = new ToolDefinition(
                "keep_worktree",
                "标记某个 worktree 为 keep：完成后不自动清理。教学版仅记录日志，实际清理由 remove_worktree 显式调用。",
                ToolDefinition.createInputSchema(
                        Map.of("name", ToolDefinition.createProperty("string", "worktree 名字")),
                        List.of("name")),
                KeepWorktreeInput.class,
                this::keepWorktree);

        return List.of(create, remove, keep);
    }

    @Override
    public List<String> promptFragments() {
        return List.of("""
                ## worktree 隔离
                - create_worktree(name, taskId)：为任务开独立工作区（分支 wt/<name> → .worktrees/<name>）
                - teammate 认领带 worktree 的 task 后，其 bash / read_file / write_file 都只作用于自己的目录
                - 收尾用 keep_worktree(name) 保留成果，或 remove_worktree(name, discard) 清理""");
    }

    public String description() {
        return "git worktree 隔离：create/keep/remove 三个工具，worktree 可绑定到任务板上的任务";
    }

    // ============================== 工具实现 ==============================

    private String createWorktree(CreateWorktreeInput in) {
        try {
            if (in == null || in.getName() == null || in.getName().isBlank()) {
                return "错误：name 不能为空";
            }
            int taskId = in.getTaskId() == null ? 0 : in.getTaskId();
            String path = worktreeManager.create(in.getName(), taskId);
            return "已创建 worktree " + in.getName() + " @ " + path;
        } catch (Exception e) {
            return "create_worktree 错误: " + e.getMessage();
        }
    }

    private String removeWorktree(RemoveWorktreeInput in) {
        try {
            if (in == null || in.getName() == null || in.getName().isBlank()) {
                return "错误：name 不能为空";
            }
            return worktreeManager.remove(in.getName(), in.isDiscard());
        } catch (Exception e) {
            return "remove_worktree 错误: " + e.getMessage();
        }
    }

    private String keepWorktree(KeepWorktreeInput in) {
        try {
            if (in == null || in.getName() == null || in.getName().isBlank()) {
                return "错误：name 不能为空";
            }
            return worktreeManager.keep(in.getName());
        } catch (Exception e) {
            return "keep_worktree 错误: " + e.getMessage();
        }
    }
}