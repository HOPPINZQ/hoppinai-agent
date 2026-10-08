package com.hoppinzq.agent.tool.background;

import com.anthropic.models.messages.MessageParam;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.schema.BackgroundTaskInput;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 后台任务管理器
 *
 * 核心功能：
 * 1. 在后台线程中执行耗时命令
 * 2. 立即返回task_id，不阻塞主线程
 * 3. 任务完成时将结果放入通知队列
 * 4. 在每次LLM调用前排空队列，注入后台任务结果
 *
 * 工作流程：
 * Main thread                Background thread
 * +-----------------+        +-----------------+
 * | agent loop      |        | task executes   |
 * | ...             |        | ...             |
 * | [LLM call] <---+------- | enqueue(result) |
 * |  ^drain queue   |        +-----------------+
 * +-----------------+
 *
 * @author hoppinzq
 */
@Slf4j
public class BackgroundManager {
    private final Map<String, BackgroundTaskInput> tasks;
    private final Queue<BackgroundNotification> notificationQueue;
    private final ExecutorService executor;
    private final AtomicInteger taskIdCounter;

    public BackgroundManager() {
        this.tasks = new ConcurrentHashMap<>();
        this.notificationQueue = new ConcurrentLinkedQueue<>();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "bg-task-" + r.hashCode());
            t.setDaemon(true);  // 守护线程，JVM退出时自动终止
            return t;
        });
        this.taskIdCounter = new AtomicInteger(0);
    }

    /**
     * 排空后台通知队列，将结果注入到消息中
     * 
     * 工作流程：
     * 1. 从后台管理器获取所有已完成的任务通知
     * 2. 将通知格式化为易读的文本
     * 3. 将格式化后的结果注入到LLM对话中
     * 4. 在控制台输出通知信息
     * 
     * 注意：此方法在每次LLM调用前被调用，确保后台任务结果能及时反馈给用户
     */
    public static void injectBackgroundNotifications(List<MessageParam> messageParams, BackgroundManager backgroundManager) {
        List<BackgroundNotification> notifications = backgroundManager.drainNotifications();

        if (!notifications.isEmpty()) {
            // 构建通知文本，使用中文状态描述
            String notifText = notifications.stream()
                    .map(n -> {
                        String statusChinese = getStatusChinese(n.getStatus());
                        return String.format("[后台任务:%s] %s: %s",
                                n.getTaskId(), statusChinese, n.getResult());
                    })
                    .collect(Collectors.joining("\n"));

            // 注入后台任务结果到LLM对话
            messageParams.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content("<后台任务结果>\n" + notifText + "\n</后台任务结果>")
                    .build());

            messageParams.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .content("已记录后台任务结果。")
                    .build());

            System.out.println("\n[后台任务通知注入]");
            System.out.println(notifText);
        }
    }

    /**
     * 将英文状态转换为中文状态描述
     */
    private static String getStatusChinese(String status) {
        switch (status) {
            case "running": return "运行中";
            case "completed": return "已完成";
            case "timeout": return "超时";
            case "error": return "错误";
            default: return status;
        }
    }

    /**
     * 启动后台任务
     * 立即返回task_id，不阻塞主线程
     * 
     * 执行步骤：
     * 1. 生成唯一任务ID
     * 2. 创建任务对象并设置初始状态
     * 3. 将任务添加到任务映射表
     * 4. 提交到线程池异步执行
     * 5. 记录日志并返回任务信息
     * 
     * @param command 要执行的命令字符串
     * @return 任务启动信息，包含任务ID和命令摘要
     */
    public String runInBackground(String command) {
        String taskId = generateTaskId();

        BackgroundTaskInput task = new BackgroundTaskInput();
        task.setTaskId(taskId);
        task.setCommand(command);
        task.setStatus("running");
        task.setStartTime(System.currentTimeMillis());

        tasks.put(taskId, task);

        // 提交后台任务到线程池异步执行
        executor.submit(() -> executeTask(task));

        log.info("启动后台任务 {}: {}", taskId, command);
        return String.format("后台任务 %s 已启动: %s", taskId,
            command.length() > 80 ? command.substring(0, 80) + "..." : command);
    }

    /**
     * 在后台线程中执行任务
     * 
     * 执行流程：
     * 1. 根据操作系统选择合适的命令解释器
     * 2. 设置工作目录为项目根目录
     * 3. 启动进程并捕获标准输出和错误输出
     * 4. 等待进程完成（最多300秒超时）
     * 5. 根据执行结果设置任务状态
     * 6. 将任务结果放入通知队列
     * 
     * 异常处理：
     * - InterruptedException: 任务被中断
     * - Timeout: 进程执行超时
     * - 其他异常: 执行过程中发生错误
     */
    private void executeTask(BackgroundTaskInput task) {
        String taskId = task.getTaskId();
        String command = task.getCommand();

        try {
            log.info("正在执行后台任务 {}: {}", taskId, command);

            ProcessBuilder processBuilder;
            // 根据操作系统选择命令解释器
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                processBuilder = new ProcessBuilder("bash", "-c", command);
            }

            // 设置工作目录为项目根目录
            processBuilder.directory(new java.io.File(AIConstants.ROOT));
            Process process = processBuilder.start();

            // 捕获标准输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 捕获错误输出
            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }

            // 等待进程完成，设置300秒超时
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);

            if (finished) {
                int exitCode = process.exitValue();
                String result = output.toString();
                if (!error.toString().isEmpty()) {
                    result += "\n错误输出:\n" + error.toString();
                }

                if (exitCode != 0) {
                    result += "\n(退出代码: " + exitCode + ")";
                }

                task.setStatus("completed");
                task.setResult(result.isEmpty() ? "(无输出)" : truncate(result, 50000));
                log.info("后台任务 {} 执行完成，退出代码: {}", taskId, exitCode);
            } else {
                task.setStatus("timeout");
                task.setResult("错误: 执行超时 (300秒)");
                process.destroyForcibly();
                log.warn("后台任务 {} 执行超时", taskId);
            }
        } catch (InterruptedException e) {
            task.setStatus("error");
            task.setResult("错误: 任务被中断");
            Thread.currentThread().interrupt();
            log.warn("后台任务 {} 被中断", taskId, e);
        } catch (Exception e) {
            task.setStatus("error");
            task.setResult("错误: " + e.getMessage());
            log.error("后台任务 {} 执行失败", taskId, e);
        }

        // 将任务结果放入通知队列，等待注入到LLM对话
        enqueueNotification(task);
    }

    /**
     * 将任务完成通知放入队列
     * 
     * 功能说明：
     * 1. 创建通知对象并设置任务信息
     * 2. 对命令和结果进行截断处理，避免数据过大
     * 3. 将通知添加到并发队列中
     * 4. 记录日志以便追踪
     * 
     * 注意：通知队列会在下次LLM调用时被排空并注入到对话中
     */
    private void enqueueNotification(BackgroundTaskInput task) {
        BackgroundNotification notification = new BackgroundNotification();
        notification.setTaskId(task.getTaskId());
        notification.setStatus(task.getStatus());
        notification.setCommand(truncate(task.getCommand(), 80));
        notification.setResult(truncate(task.getResult() != null ? task.getResult() : "(运行中)", 500));

        notificationQueue.offer(notification);
        log.info("已将任务 {} 的通知加入队列，状态: {}", task.getTaskId(), task.getStatus());
    }

    /**
     * 检查任务状态
     * 
     * 功能说明：
     * 1. 根据任务ID查找任务对象
     * 2. 如果任务不存在，返回错误信息
     * 3. 格式化任务状态信息，包括状态、命令摘要和执行结果
     * 
     * @param taskId 要检查的任务ID
     * @return 任务状态信息字符串，包含状态、命令摘要和结果
     */
    public String checkStatus(String taskId) {
        BackgroundTaskInput task = tasks.get(taskId);
        if (task == null) {
            return "错误: 未知任务 " + taskId;
        }

        StringBuilder sb = new StringBuilder();
        String statusChinese = getStatusChinese(task.getStatus());
        sb.append(String.format("[%s] %s\n", statusChinese,
            task.getCommand().length() > 60 ? task.getCommand().substring(0, 60) + "..." : task.getCommand()));
        sb.append(task.getResult() != null ? task.getResult() : "(运行中)");

        return sb.toString();
    }

    /**
     * 排空通知队列，返回所有待处理的通知
     * 在每次LLM调用前调用，注入后台任务结果
     * 
     * 执行流程：
     * 1. 从并发队列中取出所有待处理的通知
     * 2. 记录排空的通知数量
     * 3. 返回通知列表供后续处理
     * 
     * 注意：此方法是非阻塞的，会立即返回当前队列中的所有通知
     * 
     * @return 排空的通知列表，如果没有通知则返回空列表
     */
    public List<BackgroundNotification> drainNotifications() {
        List<BackgroundNotification> notifications = new ArrayList<>();

        BackgroundNotification notif;
        while ((notif = notificationQueue.poll()) != null) {
            notifications.add(notif);
        }

        if (!notifications.isEmpty()) {
            log.info("已排空 {} 个后台任务通知", notifications.size());
        }

        return notifications;
    }

    /**
     * 生成唯一的任务ID
     * 
     * 生成规则：
     * 1. 使用原子计数器确保线程安全
     * 2. 将计数器值格式化为8位十六进制字符串
     * 3. 使用掩码确保值在32位范围内
     * 
     * 示例：00000001, 00000002, ..., 0000000a, 0000000b
     * 
     * @return 8位十六进制格式的唯一任务ID
     */
    private String generateTaskId() {
        return String.format("%08x", taskIdCounter.incrementAndGet() & 0xFFFFFFFFL);
    }

    /**
     * 截断字符串到指定长度
     * 
     * 功能说明：
     * 1. 处理null值，返回空字符串
     * 2. 如果字符串长度不超过最大长度，直接返回原字符串
     * 3. 如果超过最大长度，截断并添加"..."后缀
     * 
     * 使用场景：
     * - 命令过长时截断显示
     * - 输出结果过大时截断保存
     * - 避免日志和通知数据过大
     * 
     * @param str 要截断的字符串
     * @param maxLength 最大允许长度
     * @return 截断后的字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * 获取运行中的任务数量
     * 
     * 功能说明：
     * 1. 遍历所有任务
     * 2. 筛选出状态为"running"的任务
     * 3. 统计数量并返回
     * 
     * 使用场景：
     * - 监控系统负载
     * - 限制并发任务数量
     * - 显示当前活跃任务数
     * 
     * @return 当前正在运行的后台任务数量
     */
    public int getRunningTaskCount() {
        return (int) tasks.values().stream()
            .filter(t -> "running".equals(t.getStatus()))
            .count();
    }

    /**
     * 清理已完成的旧任务（保留最近100个）
     * 
     * 清理策略：
     * 1. 当任务总数超过100个时触发清理
     * 2. 只清理已完成的任务（非运行中状态）
     * 3. 按任务开始时间排序，移除最早的任务
     * 4. 保留最近100个任务，确保历史记录不会无限增长
     * 
     * 注意：运行中的任务不会被清理，确保任务执行不受影响
     */
    public void cleanupOldTasks() {
        if (tasks.size() > 100) {
            // 按开始时间排序，移除最早完成的任务
            tasks.entrySet().stream()
                .filter(e -> !"running".equals(e.getValue().getStatus()))
                .sorted(Comparator.comparingLong(e -> e.getValue().getStartTime()))
                .limit(tasks.size() - 100)
                .forEach(e -> tasks.remove(e.getKey()));

            log.info("已清理旧的后台任务");
        }
    }

    /**
     * 关闭管理器，释放资源
     * 
     * 关闭流程：
     * 1. 优雅关闭线程池，不再接受新任务
     * 2. 等待5秒让正在执行的任务完成
     * 3. 如果超时，强制关闭所有任务
     * 4. 处理中断异常，确保线程状态正确
     * 
     * 注意：此方法应在应用程序退出前调用，确保资源正确释放
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 列出所有后台任务
     * 
     * 功能说明：
     * 1. 检查任务列表是否为空
     * 2. 遍历所有任务，格式化显示信息
     * 3. 使用任务对象的显示字符串展示详细信息
     * 
     * 显示格式：
     * 任务ID: [状态] 命令摘要
     * 
     * @return 所有后台任务的格式化字符串，如果没有任务则返回提示信息
     */
    public String listAllTasks() {
        if (tasks.isEmpty()) {
            return "暂无后台任务。";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BackgroundTaskInput> entry : tasks.entrySet()) {
            BackgroundTaskInput task = entry.getValue();
            sb.append(String.format("%s: %s\n", entry.getKey(), task.getDisplayString()));
        }

        return sb.toString().trim();
    }

    /**
     * 后台任务通知
     */
    @Data
    public static class BackgroundNotification {
        private String taskId;
        private String status;
        private String command;
        private String result;
    }
}
