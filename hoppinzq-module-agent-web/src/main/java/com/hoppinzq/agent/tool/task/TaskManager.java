package com.hoppinzq.agent.tool.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.context.SessionContextHolder;
import com.hoppinzq.agent.tool.schema.TaskInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class TaskManager {
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, Integer> sessionNextIds = new ConcurrentHashMap<>();

    public TaskManager() {
        this.mapper = new ObjectMapper();
    }

    private Path getSessionTasksDir() {
        String sessionId = SessionContextHolder.get();
        String dirName = (sessionId != null && !sessionId.isEmpty()) ? sessionId : "default";
        Path dir = Paths.get(AIConstants.ROOT, ".tasks", dirName);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("创建任务目录失败: {}", dir, e);
        }
        return dir;
    }

    private int getNextId() {
        Path tasksDir = getSessionTasksDir();
        return sessionNextIds.compute(tasksDir.toString(), (k, v) -> {
            if (v == null) {
                return findMaxId(tasksDir) + 1;
            }
            return v;
        });
    }

    private void incrementNextId() {
        Path tasksDir = getSessionTasksDir();
        sessionNextIds.compute(tasksDir.toString(), (k, v) -> (v == null) ? 1 : v + 1);
    }

    private int findMaxId(Path tasksDir) {
        try {
            return Files.list(tasksDir)
                    .filter(p -> p.getFileName().toString().startsWith("task_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> {
                        String filename = p.getFileName().toString();
                        String idStr = filename.substring(5, filename.length() - 5);
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

    public String createTask(String subject, String description) {
        Path tasksDir = getSessionTasksDir();
        TaskInfo task = new TaskInfo();
        task.setId(getNextId());
        task.setSubject(subject);
        task.setDescription(description != null ? description : "");
        task.setStatus("pending");

        saveTask(task, tasksDir);
        incrementNextId();
        log.info("创建任务 #{}: {}", task.getId(), task.getSubject());

        return taskToJson(task);
    }

    public String getTask(int taskId) {
        Path tasksDir = getSessionTasksDir();
        TaskInfo task = loadTask(taskId, tasksDir);
        return taskToJson(task);
    }

    public String updateTask(int taskId, String status, List<Integer> addBlockedBy, List<Integer> addBlocks) {
        Path tasksDir = getSessionTasksDir();
        TaskInfo task = loadTask(taskId, tasksDir);

        if (status != null && !status.isEmpty()) {
            if (!isValidStatus(status)) {
                throw new IllegalArgumentException("无效的任务状态: " + status);
            }
            task.setStatus(status);

            if ("completed".equals(status)) {
                clearDependency(taskId, tasksDir);
            }
        }

        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            Set<Integer> uniqueBlockedBy = new HashSet<>(task.getBlockedBy());
            uniqueBlockedBy.addAll(addBlockedBy);
            task.setBlockedBy(new ArrayList<>(uniqueBlockedBy));
        }

        if (addBlocks != null && !addBlocks.isEmpty()) {
            Set<Integer> uniqueBlocks = new HashSet<>(task.getBlocks());
            uniqueBlocks.addAll(addBlocks);
            task.setBlocks(new ArrayList<>(uniqueBlocks));

            for (Integer blockedId : addBlocks) {
                try {
                    TaskInfo blockedTask = loadTask(blockedId, tasksDir);
                    if (!blockedTask.getBlockedBy().contains(taskId)) {
                        blockedTask.getBlockedBy().add(taskId);
                        saveTask(blockedTask, tasksDir);
                    }
                } catch (Exception e) {
                    log.warn("更新被阻塞任务 #{} 失败", blockedId);
                }
            }
        }

        saveTask(task, tasksDir);
        return taskToJson(task);
    }

    public String listAllTasks() {
        Path tasksDir = getSessionTasksDir();
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

    private void clearDependency(int completedId, Path tasksDir) {
        try {
            Files.list(tasksDir)
                    .filter(p -> p.getFileName().toString().startsWith("task_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            TaskInfo task = loadTaskFromFile(p);
                            if (task.getBlockedBy().contains(completedId)) {
                                task.getBlockedBy().remove(Integer.valueOf(completedId));
                                saveTask(task, tasksDir);
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

    private TaskInfo loadTask(int taskId, Path tasksDir) {
        Path taskPath = tasksDir.resolve("task_" + taskId + ".json");
        if (!Files.exists(taskPath)) {
            throw new IllegalArgumentException("任务 " + taskId + " 未找到");
        }
        return loadTaskFromFile(taskPath);
    }

    private TaskInfo loadTaskFromFile(Path path) {
        try {
            return mapper.readValue(path.toFile(), TaskInfo.class);
        } catch (IOException e) {
            log.error("从文件加载任务失败: {}", path, e);
            throw new RuntimeException("加载任务失败", e);
        }
    }

    private void saveTask(TaskInfo task, Path tasksDir) {
        Path taskPath = tasksDir.resolve("task_" + task.getId() + ".json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(taskPath.toFile(), task);
        } catch (IOException e) {
            log.error("保存任务 #{} 失败", task.getId(), e);
            throw new RuntimeException("保存任务失败", e);
        }
    }

    private String taskToJson(TaskInfo task) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
        } catch (IOException e) {
            return "{ \"error\": \"序列化任务失败\" }";
        }
    }

    private boolean isValidStatus(String status) {
        return "pending".equals(status) || "in_progress".equals(status) || "completed".equals(status);
    }

    public List<TaskInfo> getReadyTasks() {
        Path tasksDir = getSessionTasksDir();
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

    public int getTaskCount() {
        Path tasksDir = getSessionTasksDir();
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
