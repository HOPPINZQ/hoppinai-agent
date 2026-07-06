package com.hoppinzq.agent.tool.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.schema.TaskInput;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务管理器
 *
 * 核心功能：
 * 1. 任务持久化到磁盘（.tasks目录）
 * 2. 依赖关系管理（blockedBy和blocks）
 * 3. 状态管理（pending -> in_progress -> completed）
 * 4. 依赖解析（完成任务时自动解除依赖）
 *
 * @author hoppinzq
 */
@Slf4j
public class TaskManager {
    private final Path tasksDir;
    private final ObjectMapper mapper;
    private String currentSessionId;

    public TaskManager() {
        this.tasksDir = Paths.get(AIConstants.ROOT, ".tasks");
        this.mapper = new ObjectMapper();
        try {
            Files.createDirectories(tasksDir);
        } catch (IOException e) {
            // 只在初始化失败时记录日志
            log.error("创建任务目录失败", e);
        }
    }

    /**
     * 设置当前会话ID
     */
    public void setSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }

    /**
     * 创建新任务
     */
    public String createTask(String subject, String description, List<String> blockedBy) {
        String taskId = UUID.randomUUID().toString();
        TaskInput task = new TaskInput();
        task.setId(taskId);
        task.setSessionId(currentSessionId);
        task.setSubject(subject);
        task.setDescription(description != null ? description : "");
        task.setStatus("pending");

        // 处理blockedBy - 将短ID转换为完整ID
        if (blockedBy != null && !blockedBy.isEmpty()) {
            List<String> fullBlockedBy = new ArrayList<>();
            for (String blockedId : blockedBy) {
                String fullId = resolveTaskId(blockedId);
                if (fullId != null) {
                    fullBlockedBy.add(fullId);
                }
            }
            task.setBlockedBy(fullBlockedBy);
        }

        saveTask(task);
        return formatResult("✅ 创建任务", task.getShortId() + ": " + subject);
    }

    /**
     * 获取任务详情
     */
    public String getTask(String taskId) {
        TaskInput task = loadTask(taskId);
        StringBuilder sb = new StringBuilder();
        sb.append("📄 任务详情\n");
        sb.append("════════════════════════════════════════\n");
        sb.append("ID: ").append(task.getShortId()).append("\n");
        sb.append("标题: ").append(task.getSubject()).append("\n");
        sb.append("状态: ").append(task.getStatusText()).append("\n");
        if (task.getOwner() != null && !task.getOwner().isEmpty()) {
            sb.append("所有者: ").append(task.getOwner()).append("\n");
        }
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            sb.append("描述: ").append(task.getDescription()).append("\n");
        }
        if (!task.getBlockedBy().isEmpty()) {
            sb.append("阻塞: ").append(formatIdList(task.getBlockedBy())).append("\n");
        }
        sb.append("════════════════════════════════════════\n");
        sb.append("\n");
        sb.append(listAllTasks());
        return sb.toString();
    }

    /**
     * 更新任务
     */
    public String updateTask(String taskId, String status, List<String> addBlockedBy, List<String> addBlocks) {
        TaskInput task = loadTask(taskId);

        // 更新状态
        if (status != null && !status.isEmpty()) {
            if (!isValidStatus(status)) {
                throw new IllegalArgumentException("无效的任务状态: " + status);
            }
            task.setStatus(status);

            // 如果任务完成，清除依赖关系
            if ("completed".equals(status)) {
                clearDependency(task.getId());
            }
        }

        // 添加阻塞依赖
        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            Set<String> uniqueBlockedBy = new HashSet<>(task.getBlockedBy());
            for (String blockedId : addBlockedBy) {
                String fullId = resolveTaskId(blockedId);
                if (fullId != null) {
                    uniqueBlockedBy.add(fullId);
                }
            }
            task.setBlockedBy(new ArrayList<>(uniqueBlockedBy));
        }

        // 添加阻塞的任务
        if (addBlocks != null && !addBlocks.isEmpty()) {
            Set<String> uniqueBlocks = new HashSet<>(task.getBlocks());
            for (String blockId : addBlocks) {
                String fullId = resolveTaskId(blockId);
                if (fullId != null) {
                    uniqueBlocks.add(fullId);
                }
            }
            task.setBlocks(new ArrayList<>(uniqueBlocks));

            // 双向更新：更新被阻塞任务的blockedBy列表
            for (String blockedId : uniqueBlocks) {
                try {
                    TaskInput blockedTask = loadTask(blockedId);
                    if (!blockedTask.getBlockedBy().contains(task.getId())) {
                        blockedTask.getBlockedBy().add(task.getId());
                        saveTask(blockedTask);
                    }
                } catch (Exception e) {
                    // 静默处理
                }
            }
        }

        saveTask(task);
        return formatResult("✅ 更新任务", task.getShortId() + ": " + task.getSubject());
    }

    /**
     * 列出所有任务
     */
    public String listAllTasks() {
        try {
            List<TaskInput> tasks = Files.list(tasksDir)
                .filter(p -> p.getFileName().toString().startsWith("task_"))
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .map(this::loadTaskFromFile)
                .sorted(Comparator.comparingLong(TaskInput::getCreatedAt))
                .toList();

            if (tasks.isEmpty()) {
                return "暂无任务。使用 task_create 创建新任务。";
            }

            // 统计信息
            long pendingCount = tasks.stream().filter(t -> "pending".equals(t.getStatus())).count();
            long inProgressCount = tasks.stream().filter(t -> "in_progress".equals(t.getStatus())).count();
            long completedCount = tasks.stream().filter(t -> "completed".equals(t.getStatus())).count();

            StringBuilder sb = new StringBuilder();
            sb.append("╔════════════════════════════════════════════════════════════════╗\n");
            sb.append("║                       📋 任务列表                                 ║\n");
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");
            sb.append(String.format("║ 总计: %d  │  ○ 待处理: %d  │  ● 进行中: %d  │  ✓ 已完成: %d   ║\n",
                    tasks.size(), pendingCount, inProgressCount, completedCount));
            sb.append("╠════════════════════════════════════════════════════════════════╣\n");

            for (TaskInput task : tasks) {
                sb.append("║ ").append(task.getDisplayString()).append("\n");
            }

            sb.append("╚════════════════════════════════════════════════════════════════╝\n");

            // 添加可执行任务提示
            List<TaskInput> readyTasks = tasks.stream()
                    .filter(t -> "pending".equals(t.getStatus()))
                    .filter(t -> t.getBlockedBy().isEmpty())
                    .toList();

            if (!readyTasks.isEmpty()) {
                sb.append("\n🟢 可立即执行的任务: ");
                sb.append(readyTasks.stream()
                        .map(t -> t.getShortId())
                        .collect(Collectors.joining(", ")));
                sb.append("\n");
            }

            return sb.toString();
        } catch (IOException e) {
            return "列出任务时出错: " + e.getMessage();
        }
    }

    /**
     * 清除依赖关系（当任务完成时）
     */
    private void clearDependency(String completedId) {
        try {
            Files.list(tasksDir)
                .filter(p -> p.getFileName().toString().startsWith("task_"))
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(p -> {
                    try {
                        TaskInput task = loadTaskFromFile(p);
                        if (task.getBlockedBy().contains(completedId)) {
                            task.getBlockedBy().remove(completedId);
                            saveTask(task);
                        }
                    } catch (Exception e) {
                        // 静默处理
                    }
                });
        } catch (IOException e) {
            // 静默处理
        }
    }

    /**
     * 加载任务
     */
    private TaskInput loadTask(String taskId) {
        String fullId = resolveTaskId(taskId);
        if (fullId == null) {
            throw new IllegalArgumentException("任务 " + taskId + " 未找到");
        }
        Path taskPath = tasksDir.resolve("task_" + fullId + ".json");
        if (!Files.exists(taskPath)) {
            throw new IllegalArgumentException("任务 " + taskId + " 未找到");
        }
        return loadTaskFromFile(taskPath);
    }

    /**
     * 从文件加载任务
     */
    private TaskInput loadTaskFromFile(Path path) {
        try {
            return mapper.readValue(path.toFile(), TaskInput.class);
        } catch (IOException e) {
            throw new RuntimeException("加载任务失败", e);
        }
    }

    /**
     * 保存任务
     */
    private void saveTask(TaskInput task) {
        Path taskPath = tasksDir.resolve("task_" + task.getId() + ".json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(taskPath.toFile(), task);
        } catch (IOException e) {
            throw new RuntimeException("保存任务失败", e);
        }
    }

    /**
     * 验证状态是否有效
     */
    private boolean isValidStatus(String status) {
        return "pending".equals(status) || "in_progress".equals(status) || "completed".equals(status);
    }

    /**
     * 检查任务是否可以开始（所有 blockedBy 依赖是否已完成）
     */
    public boolean canStart(String taskId) {
        TaskInput task = loadTask(taskId);
        for (String depId : task.getBlockedBy()) {
            Path depPath = tasksDir.resolve("task_" + depId + ".json");
            if (!Files.exists(depPath)) {
                return false;
            }
            TaskInput depTask = loadTaskFromFile(depPath);
            if (!"completed".equals(depTask.getStatus())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 认领任务
     */
    public String claimTask(String taskId, String owner) {
        TaskInput task = loadTask(taskId);

        if (!"pending".equals(task.getStatus())) {
            return formatError("任务 " + task.getShortId() + " 当前状态为「" + task.getStatusText() + "」，无法认领");
        }

        if (!canStart(taskId)) {
            List<String> blockingTasks = new ArrayList<>();
            for (String depId : task.getBlockedBy()) {
                Path depPath = tasksDir.resolve("task_" + depId + ".json");
                if (!Files.exists(depPath)) {
                    blockingTasks.add(depId.substring(0, 8));
                } else {
                    TaskInput depTask = loadTaskFromFile(depPath);
                    if (!"completed".equals(depTask.getStatus())) {
                        blockingTasks.add(depId.substring(0, 8));
                    }
                }
            }
            return formatError("任务 " + task.getShortId() + " 被以下任务阻塞: " +
                    blockingTasks.stream().collect(Collectors.joining(", ")));
        }

        task.setOwner(owner != null ? owner : "agent");
        task.setStatus("in_progress");
        saveTask(task);

        return formatResult("✅ 认领任务", task.getShortId() + ": " + task.getSubject() + " → 进行中");
    }

    /**
     * 完成任务
     */
    public String completeTask(String taskId) {
        TaskInput task = loadTask(taskId);

        if (!"in_progress".equals(task.getStatus())) {
            return formatError("任务 " + task.getShortId() + " 当前状态为「" + task.getStatusText() + "」，无法完成");
        }

        task.setStatus("completed");
        saveTask(task);

        // 清除依赖关系
        clearDependency(taskId);

        // 查找被解除阻塞的下游任务
        List<String> unblockedTasks = new ArrayList<>();
        try {
            List<TaskInput> allTasks = Files.list(tasksDir)
                    .filter(p -> p.getFileName().toString().startsWith("task_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(this::loadTaskFromFile)
                    .toList();

            for (TaskInput t : allTasks) {
                if ("pending".equals(t.getStatus()) &&
                    !t.getBlockedBy().isEmpty() &&
                    canStart(t.getId())) {
                    unblockedTasks.add(t.getShortId() + ": " + t.getSubject());
                }
            }
        } catch (IOException e) {
            // 静默处理
        }

        StringBuilder result = new StringBuilder();
        result.append(formatResult("✅ 完成任务", task.getShortId() + ": " + task.getSubject()));

        if (!unblockedTasks.isEmpty()) {
            result.append("\n\n🔓 解除阻塞，以下任务现在可以开始:\n");
            for (String unblocked : unblockedTasks) {
                result.append("   • ").append(unblocked).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * 获取任务数量
     */
    public int getTaskCount() {
        try {
            return (int) Files.list(tasksDir)
                .filter(p -> p.getFileName().toString().startsWith("task_"))
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 解析任务ID（支持短ID和完整ID）
     */
    private String resolveTaskId(String shortId) {
        // 如果是完整UUID，直接返回
        if (shortId.length() == 36) {
            return shortId;
        }
        // 否则查找匹配的任务
        try {
            return Files.list(tasksDir)
                .filter(p -> p.getFileName().toString().startsWith("task_"))
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().substring(5, 41)) // 去掉 "task_" 和 ".json"
                .filter(id -> id.startsWith(shortId))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 格式化ID列表
     */
    private String formatIdList(List<String> ids) {
        return ids.stream()
                .map(id -> id.substring(0, Math.min(8, id.length())))
                .collect(Collectors.joining(", "));
    }

    /**
     * 格式化操作结果（附带完整任务列表）
     */
    private String formatResult(String operation, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append(operation).append(": ").append(detail).append("\n\n");
        sb.append(listAllTasks());
        return sb.toString();
    }

    /**
     * 格式化错误结果
     */
    private String formatError(String message) {
        return "❌ " + message + "\n\n" + listAllTasks();
    }
}
