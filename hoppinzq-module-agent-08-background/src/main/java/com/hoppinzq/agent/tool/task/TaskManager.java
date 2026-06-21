package com.hoppinzq.agent.tool.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.schema.TaskInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务管理器
 * <p>
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
    private int nextId;

    public TaskManager() {
        this.tasksDir = Paths.get(AIConstants.ROOT, ".tasks");
        this.mapper = new ObjectMapper();
        try {
            Files.createDirectories(tasksDir);
        } catch (IOException e) {
            log.error("创建任务目录失败", e);
        }
        this.nextId = findMaxId() + 1;
    }

    /**
     * 查找当前最大的任务ID
     */
    private int findMaxId() {
        try {
            return Files.list(tasksDir)
                    .filter(p -> p.getFileName().toString().startsWith("task_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> {
                        String filename = p.getFileName().toString();
                        String idStr = filename.substring(5, filename.length() - 5); // task_{id}.json
                        try {
                            return Integer.parseInt(idStr);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    })
                    .max(Integer::compareTo)
                    .orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 创建新任务
     */
    public String createTask(String subject, String description) {
        TaskInfo task = new TaskInfo();
        task.setId(nextId++);
        task.setSubject(subject);
        task.setDescription(description != null ? description : "");
        task.setStatus("pending");

        saveTask(task);
        log.info("创建任务 #{}: {}", task.getId(), task.getSubject());

        return taskToJson(task);
    }

    /**
     * 获取任务详情
     */
    public String getTask(int taskId) {
        TaskInfo task = loadTask(taskId);
        return taskToJson(task);
    }

    /**
     * 更新任务
     */
    public String updateTask(int taskId, String status, List<Integer> addBlockedBy, List<Integer> addBlocks) {
        TaskInfo task = loadTask(taskId);

        // 更新状态
        if (status != null && !status.isEmpty()) {
            if (!isValidStatus(status)) {
                throw new IllegalArgumentException("无效的任务状态: " + status);
            }
            task.setStatus(status);

            // 如果任务完成，清除依赖关系
            if ("completed".equals(status)) {
                clearDependency(taskId);
            }
        }

        // 添加阻塞依赖
        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            Set<Integer> uniqueBlockedBy = new HashSet<>(task.getBlockedBy());
            uniqueBlockedBy.addAll(addBlockedBy);
            task.setBlockedBy(new ArrayList<>(uniqueBlockedBy));
        }

        // 添加阻塞的任务
        if (addBlocks != null && !addBlocks.isEmpty()) {
            Set<Integer> uniqueBlocks = new HashSet<>(task.getBlocks());
            uniqueBlocks.addAll(addBlocks);
            task.setBlocks(new ArrayList<>(uniqueBlocks));

            // 双向更新：更新被阻塞任务的blockedBy列表
            for (Integer blockedId : addBlocks) {
                try {
                    TaskInfo blockedTask = loadTask(blockedId);
                    if (!blockedTask.getBlockedBy().contains(taskId)) {
                        blockedTask.getBlockedBy().add(taskId);
                        saveTask(blockedTask);
                    }
                } catch (Exception e) {
                    log.warn("更新被阻塞任务 #{} 失败", blockedId);
                }
            }
        }

        saveTask(task);
        return taskToJson(task);
    }

    /**
     * 列出所有任务
     */
    public String listAllTasks() {
        try {
            List<TaskInfo> tasks = Files.list(tasksDir)
                    .filter(p -> p.getFileName().toString().startsWith("task_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(this::loadTaskFromFile)
                    .sorted(Comparator.comparingInt(TaskInfo::getId))
                    .toList();

            if (tasks.isEmpty()) {
                return "暂无任务。";
            }

            return tasks.stream()
                    .map(TaskInfo::getDisplayString)
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "列出任务时出错: " + e.getMessage();
        }
    }

    /**
     * 清除依赖关系（当任务完成时）
     */
    private void clearDependency(int completedId) {
        try {
            Files.list(tasksDir)
                    .filter(p -> p.getFileName().toString().startsWith("task_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            TaskInfo task = loadTaskFromFile(p);
                            if (task.getBlockedBy().contains(completedId)) {
                                task.getBlockedBy().remove(Integer.valueOf(completedId));
                                saveTask(task);
                                log.info("解除任务 #{} 的阻塞，前置任务 #{} 已完成", task.getId(), completedId);
                            }
                        } catch (Exception e) {
                            log.warn("清除任务依赖失败", e);
                        }
                    });
        } catch (IOException e) {
            log.error("清除依赖关系失败", e);
        }
    }

    /**
     * 加载任务
     */
    private TaskInfo loadTask(int taskId) {
        Path taskPath = tasksDir.resolve("task_" + taskId + ".json");
        if (!Files.exists(taskPath)) {
            throw new IllegalArgumentException("任务 " + taskId + " 未找到");
        }
        return loadTaskFromFile(taskPath);
    }

    /**
     * 从文件加载任务
     */
    private TaskInfo loadTaskFromFile(Path path) {
        try {
            return mapper.readValue(path.toFile(), TaskInfo.class);
        } catch (IOException e) {
            log.error("从文件加载任务失败: {}", path, e);
            throw new RuntimeException("加载任务失败", e);
        }
    }

    /**
     * 保存任务
     */
    private void saveTask(TaskInfo task) {
        Path taskPath = tasksDir.resolve("task_" + task.getId() + ".json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(taskPath.toFile(), task);
        } catch (IOException e) {
            log.error("保存任务 #{} 失败", task.getId(), e);
            throw new RuntimeException("保存任务失败", e);
        }
    }

    /**
     * 将任务转换为JSON字符串
     */
    private String taskToJson(TaskInfo task) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
        } catch (IOException e) {
            return "{ \"error\": \"序列化任务失败\" }";
        }
    }

    /**
     * 验证状态是否有效
     */
    private boolean isValidStatus(String status) {
        return "pending".equals(status) || "in_progress".equals(status) || "completed".equals(status);
    }

    /**
     * 获取可运行的任务（没有被阻塞的pending任务）
     */
    public List<TaskInfo> getReadyTasks() {
        try {
            return Files.list(tasksDir)
                    .filter(p -> p.getFileName().toString().startsWith("task_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(this::loadTaskFromFile)
                    .filter(t -> "pending".equals(t.getStatus()))
                    .filter(t -> t.getBlockedBy().isEmpty())
                    .sorted(Comparator.comparingInt(TaskInfo::getId))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
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
}
