package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.bus.MessageBus;
import com.hoppinzq.agent.tool.bus.TeammateSpawner;
import com.hoppinzq.agent.tool.cron.CronExpression;
import com.hoppinzq.agent.tool.cron.CronJob;
import com.hoppinzq.agent.tool.cron.CronScheduler;
import com.hoppinzq.agent.tool.protocol.ProtocolRegistry;
import com.hoppinzq.agent.tool.schema.*;
import com.hoppinzq.agent.tool.task.TaskManager;
import com.hoppinzq.agent.tool.util.FileExclusionHelper;
import com.hoppinzq.agent.tool.worktree.WorktreeContext;
import com.hoppinzq.agent.tool.worktree.WorktreeManager;
import com.anthropic.client.AnthropicClient;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * @author hoppinzq
 */
@Slf4j
public class Tools {

    /** cron 调度器单例，由 AgentCron.main 注入；为空时 schedule/list/cron 工具会报错 */
    private static volatile CronScheduler CRON_SCHEDULER;

    public static void setCronScheduler(CronScheduler scheduler) {
        CRON_SCHEDULER = scheduler;
    }

    public static CronScheduler getCronScheduler() {
        return CRON_SCHEDULER;
    }

    // ========================= teams / protocols 注入位 =========================

    /** lead 的 LLM client，spawn_teammate 时复用 */
    private static volatile AnthropicClient LEAD_CLIENT;
    /** lead 使用的模型名 */
    private static volatile String LEAD_MODEL;
    /** lead 与 teammate 共享的消息总线 */
    private static volatile MessageBus LEAD_BUS;
    /** teammate 可用工具列表（spawn 时拷贝一份传给 TeammateRunner） */
    private static volatile List<ToolDefinition> LEAD_TEAMMATE_TOOLS;
    /** lead 的协议注册表 */
    private static volatile ProtocolRegistry LEAD_REGISTRY;
    /** 当前调用 send_message 的 teammate 名字（teammate 线程内设置；lead 调用时为 null，代表 from=lead） */
    public static final ThreadLocal<String> CURRENT_TEAMMATE_NAME = new ThreadLocal<>();

    public static void setLeadClient(AnthropicClient client) { LEAD_CLIENT = client; }
    public static void setLeadModel(String model) { LEAD_MODEL = model; }
    public static void setLeadBus(MessageBus bus) { LEAD_BUS = bus; }
    public static void setLeadTeammateTools(List<ToolDefinition> tools) { LEAD_TEAMMATE_TOOLS = tools; }
    public static void setLeadRegistry(ProtocolRegistry registry) { LEAD_REGISTRY = registry; }

    /** 任务管理器单例 */
    private static volatile TaskManager TASK_MANAGER;
    public static void setTaskManager(TaskManager tm) { TASK_MANAGER = tm; }
    public static TaskManager getTaskManager() { return TASK_MANAGER; }

    /** worktree 管理器单例 */
    private static volatile WorktreeManager WORKTREE_MANAGER;
    public static void setWorktreeManager(WorktreeManager wm) { WORKTREE_MANAGER = wm; }
    public static WorktreeManager getWorktreeManager() { return WORKTREE_MANAGER; }

    /**
     * schedule_cron：注册一个 cron 任务。
     */
    public static String scheduleCron(String input) {
        if (CRON_SCHEDULER == null) {
            return "错误：cron 调度器未初始化";
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            CronScheduleInput in = mapper.readValue(input, CronScheduleInput.class);
            if (in.getCron() == null || in.getCron().isBlank()) {
                return "错误：cron 表达式不能为空";
            }
            if (in.getPrompt() == null || in.getPrompt().isBlank()) {
                return "错误：prompt 不能为空";
            }
            // 先验证表达式可解析
            try {
                new CronExpression(in.getCron());
            } catch (IllegalArgumentException e) {
                return "错误：cron 表达式无效 — " + e.getMessage();
            }
            CronJob job = CronJob.builder()
                    .cron(in.getCron())
                    .prompt(in.getPrompt())
                    .recurring(in.isRecurring())
                    .durable(in.isDurable())
                    .build();
            String id = CRON_SCHEDULER.scheduleJob(job);
            return "已注册 cron 任务 id=" + id + " cron='" + in.getCron() + "' recurring=" + in.isRecurring()
                    + " durable=" + in.isDurable();
        } catch (Exception e) {
            return "schedule_cron 错误: " + e.getMessage();
        }
    }

    /**
     * list_crons：列出所有 cron 任务。
     */
    public static String listCrons(String input) {
        if (CRON_SCHEDULER == null) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(CRON_SCHEDULER.listJobs());
        } catch (Exception e) {
            return "list_crons 错误: " + e.getMessage();
        }
    }

    /**
     * cancel_cron：按 id 取消一个 cron 任务。
     */
    public static String cancelCron(String input) {
        if (CRON_SCHEDULER == null) {
            return "错误：cron 调度器未初始化";
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            CronCancelInput in = mapper.readValue(input, CronCancelInput.class);
            if (in.getId() == null || in.getId().isBlank()) {
                return "错误：id 不能为空";
            }
            boolean ok = CRON_SCHEDULER.cancelJob(in.getId());
            return ok ? "已取消 cron 任务 " + in.getId() : "未找到 cron 任务 " + in.getId();
        } catch (Exception e) {
            return "cancel_cron 错误: " + e.getMessage();
        }
    }

    /**
     * 读取指定文件内容，支持./../相对路径
     * @param input
     * @return
     */
    public static String readFile(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ReadFileInput readFileInput = mapper.readValue(input, ReadFileInput.class);

            if (LOG_ENABLE) {
                log.info("读取文件: {}", readFileInput.getPath());
            }
            Path currentPath = WorktreeContext.effectiveRoot().toPath();
            if (LOG_ENABLE) {
                log.info("当前工作目录: {}", currentPath);
            }
            Path fullPath = currentPath.resolve(readFileInput.getPath()).normalize();
            byte[] bytes = Files.readAllBytes(fullPath);
            String content = new String(bytes);

            if (LOG_ENABLE) {
                log.info("读取文件成功 {} ({} bytes)", readFileInput.getPath(), content.length());
            }

            return content;
        } catch (Exception e) {
            if (LOG_ENABLE) {
                log.error("错误: {}", e.getMessage());
            }
            return "读取文件错误: " + e.getMessage();
        }
    }

    /**
     * 写入内容到指定文件，支持./../相对路径
     * @param input
     * @return
     */
    public static String writeFile(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            WriteFileInput writeFileInput = mapper.readValue(input, WriteFileInput.class);

            if (LOG_ENABLE) {
                log.info("写入文件: {}", writeFileInput.getPath());
            }
            Path currentPath = WorktreeContext.effectiveRoot().toPath();
            Path fullPath = currentPath.resolve(writeFileInput.getPath()).normalize();

            // Ensure parent directories exist
            if (fullPath.getParent() != null) {
                Files.createDirectories(fullPath.getParent());
            }

            Files.write(fullPath, writeFileInput.getContent().getBytes(StandardCharsets.UTF_8));

            if (LOG_ENABLE) {
                log.info("写入文件成功 {} ({} bytes)", writeFileInput.getPath(), writeFileInput.getContent().length());
            }

            return "已写入 " + writeFileInput.getContent().length() + " 字节到 " + writeFileInput.getPath();
        } catch (Exception e) {
            if (LOG_ENABLE) {
                log.error("写入文件错误: {}", e.getMessage());
            }
            return "写入文件错误: " + e.getMessage();
        }
    }

    /**
     * 执行命令并返回结果
     * 该函数接收一个JSON格式的输入字符串，解析出要执行的命令和命令类型，
     * 根据指定的类型（cmd/powershell/bash）使用不同的方式执行命令，
     * 并捕获命令的标准输出和错误输出。
     *
     * 支持的命令类型：
     * - cmd: Windows CMD 命令提示符
     * - powershell: Windows PowerShell
     * - bash: Linux/Mac Bash shell（或 Windows Git Bash）
     *
     * 如果不指定类型，则根据操作系统自动选择：
     * - Windows: 默认使用 cmd
     * - Linux/Mac: 默认使用 bash
     *
     * @param input JSON格式的输入字符串，包含：
     *              - command: 要执行的命令（必填）
     *              - type: 命令类型，可选值为 "cmd"、"powershell"、"bash"（可选）
     * @return 命令执行结果字符串，包含标准输出和错误信息（如果有）；如果执行失败，会包含错误码和错误信息
     */
    public static String executeBash(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            BashInput bashInput = mapper.readValue(input, BashInput.class);

            if (LOG_ENABLE) {
                log.info("执行指令: {}, 类型: {}", bashInput.getCommand(), bashInput.getType());
            }

            ProcessBuilder processBuilder;
            String type = bashInput.getType();
            String command = bashInput.getCommand();

            // 根据指定的类型或操作系统选择执行方式
            if (type != null && !type.isEmpty()) {
                switch (type.toLowerCase()) {
                    case "cmd":
                        processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
                        break;
                    case "powershell":
                        processBuilder = new ProcessBuilder("powershell.exe", "-Command", command);
                        break;
                    case "bash":
                        processBuilder = new ProcessBuilder("bash", "-c", command);
                        break;
                    default:
                        return "错误: 不支持的命令类型 '" + type + "'，仅支持 cmd、powershell 或 bash";
                }
            } else {
                // 未指定类型时，根据操作系统自动选择
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
                } else {
                    processBuilder = new ProcessBuilder("bash", "-c", command);
                }
            }

            // worktree 隔离：teammate 线程内把 cwd 切到 worktree 目录
            processBuilder.directory(WorktreeContext.effectiveRoot());
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), "GBK"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            String result = output.toString();
            if (!error.toString().isEmpty()) {
                result += "错误:\n" + error;
            }

            if (exitCode != 0) {
                if (LOG_ENABLE) {
                    log.error("执行指令失败: {}, 类型: {}, exitCode: {}", command, type, exitCode);
                }
                return "执行指令失败，exitCode: " + exitCode + "\n输出: " + result;
            }

            if (LOG_ENABLE) {
                log.info("执行指令成功: {} (输出: {} bytes)", command, result.length());
            }

            return result.trim();
        } catch (Exception e) {
            if (LOG_ENABLE) {
                log.error("执行指令失败: {}", e.getMessage());
            }
            return "执行指令失败: " + e.getMessage();
        }
    }

    /**
     * 编辑文件内容，支持替换或追加操作
     * 功能说明：
     * 1. 解析输入的JSON字符串为EditFileInput对象
     * 2. 验证输入参数有效性（路径非空且新旧字符串不同）
     * 3. 读取目标文件内容，处理文件不存在的情况
     * 4. 执行替换或追加操作：
     *    - 当oldStr为空时直接追加newStr
     *    - 当oldStr存在且唯一时执行替换
     *    - 当oldStr不存在或出现多次时报错
     * 5. 将修改后的内容写回文件
     *
     * @param input JSON格式的输入字符串，包含：
     *              - path: 文件路径（必填）
     *              - oldStr: 要被替换的字符串（可选）
     *              - newStr: 要写入的新字符串（必填）
     * @return 操作结果字符串：
     *         - "OK" 表示成功
     *         - 错误信息字符串（如参数无效、文件读取错误等）
     */
    public static String editFile(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            EditFileInput editFileInput = mapper.readValue(input, EditFileInput.class);
            if (editFileInput.getPath() == null || editFileInput.getPath().isEmpty() ||
                    editFileInput.getOldStr().equals(editFileInput.getNewStr())) {
                if (LOG_ENABLE) {
                    log.error("编辑文件失败: 无效的入参");
                }
                return "错误: 无效的入参";
            }

            if (LOG_ENABLE) {
                log.info("编辑文件: {} (替换 {} 内容 ，新增 {} 内容)",
                        editFileInput.getPath(), editFileInput.getOldStr(), editFileInput.getNewStr());
            }

            Path filePath = Paths.get(WorktreeContext.effectiveRoot().getAbsolutePath()+File.separator+editFileInput.getPath());
            String oldContent;

            try {
                oldContent = new String(Files.readAllBytes(filePath));
            } catch (IOException e) {
                // 文件不存在，或者oldStr为空则创建新文件
                if (editFileInput.getOldStr() == null || editFileInput.getOldStr().isEmpty()) {
                    return createNewFile(filePath, editFileInput.getNewStr());
                }
                if (LOG_ENABLE) {
                    log.error("读取文件错误 {}: {}", editFileInput.getPath(), e.getMessage());
                }
                return "读取文件错误: " + e.getMessage();
            }

            // oldStr为空，直接追加newStr
            String newContent;
            if (editFileInput.getOldStr() == null || editFileInput.getOldStr().isEmpty()) {
                newContent = oldContent + editFileInput.getNewStr();
            } else {
                // oldStr不存在时（往往AI在生成的过程中，用户也可以在修改一些内容），报错
                int count = countOccurrences(oldContent, editFileInput.getOldStr());
                if (count == 0) {
                    if (LOG_ENABLE) {
                        log.error("编辑文件错误，oldStr没有在文件里 {}", editFileInput.getPath());
                    }
                    return "错误: oldStr没有在文件里";
                }
                // oldStr出现多次时，报错
                if (count > 1) {
                    if (LOG_ENABLE) {
                        log.error("编辑文件错误: oldStr出现 {} 次在文件里 {}, 应该只有一次",
                                count, editFileInput.getPath());
                    }
                    return "错误: oldStr出现 " + count + " 次在文件里, 应该只有一次";
                }

                // 检测原文件的换行符类型
                boolean usesWindowsLineEnding = oldContent.contains("\r\n");

                // 归一化处理：统一为 \n 换行符，处理制表符和空格
                String normalizedOldContent = normalizeForReplace(oldContent);
                String normalizedOldStr = normalizeForReplace(editFileInput.getOldStr());
                String normalizedNewStr = normalizeForReplace(editFileInput.getNewStr());

                // 使用归一化后的字符串进行替换
                String normalizedNewContent = normalizedOldContent.replace(normalizedOldStr, normalizedNewStr);

                // 恢复原文件的换行符格式
                newContent = usesWindowsLineEnding
                        ? normalizedNewContent.replace("\n", "\r\n")
                        : normalizedNewContent;
            }

            Files.write(filePath, newContent.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            if (LOG_ENABLE) {
                log.info("编辑文件成功 {}", editFileInput.getPath());
            }

            return "OK";
        } catch (Exception e) {
            if (LOG_ENABLE) {
                log.error("编辑文件失败: {}", e.getMessage());
            }
            return "编辑文件失败: " + e.getMessage();
        }
    }

    /**
     * 列出指定目录下的所有文件和子目录（递归遍历）
     * 可跳过指定前缀的目录
     *
     * @param input JSON格式的输入参数，包含要遍历的目录路径（path字段）
     *                如果path为空或null，则默认使用当前目录(".")
     * @return JSON格式的字符串，包含所有找到的文件和目录的相对路径列表
     *         如果发生错误，返回错误信息字符串
     */
    public static String listFiles(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ListFilesInput listFilesInput = mapper.readValue(input, ListFilesInput.class);

            String dir = WorktreeContext.effectiveRoot().getAbsolutePath();
            String fileType;
            if (listFilesInput.getPath() != null && !listFilesInput.getPath().isEmpty()) {
                dir = WorktreeContext.effectiveRoot().getAbsolutePath() + File.separator + listFilesInput.getPath();
            }
            if (listFilesInput.getFileType() != null) {
                fileType = listFilesInput.getFileType();
            } else {
                fileType = null;
            }
            if (AIConstants.LOG_ENABLE) {
                log.info("列出文件: {}", dir);
            }

            ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
            Path startPath = Paths.get(dir).toAbsolutePath();

            // 定义要排除的目录和文件
            Set<String> excludedDirs = Set.of(".idea", ".git", "target", "node_modules",
                    "build", "dist", "out", "bin", "tmp", "temp", "cache", "logs", "zq_ai_ignores", "hoppinzq-html");

            Set<String> excludedFilePatterns = Set.of(
                    "*.iml", "**/*.iml",
                    "**/test/**"
            );

            FileExclusionHelper exclusionHelper = new FileExclusionHelper(excludedDirs, excludedFilePatterns);

            try (Stream<Path> stream = Files.walk(startPath)) {
                stream.forEach(path -> {
                    try {
                        Path relativePath = startPath.relativize(path);
                        Path absolutePath = relativePath.toAbsolutePath();
                        String relativePathStr = relativePath.toString();
                        String absolutePathStr = absolutePath.toString();
                        String fileOrDirName = relativePath.getFileName().toString();
                        ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
                        objectNode.put("path", relativePathStr);
                        objectNode.put("absolutePath", absolutePathStr);

                        // 使用排除助手检查
                        boolean shouldExclude = exclusionHelper.shouldExclude(relativePathStr, Files.isDirectory(path));

                        if (shouldExclude) {
                            return;
                        }

                        if (!relativePathStr.isEmpty()) {
                            if (Files.isDirectory(path)) {
                                if (fileType == null || fileType.isEmpty()) {
                                    objectNode.put("type", "directory");
                                    objectNode.put("dirName", fileOrDirName);
                                    arrayNode.add(objectNode);
                                }
                            } else {
                                String ext = "";
                                int dotIndex = relativePathStr.lastIndexOf(".");
                                if (dotIndex > 0) {
                                    ext = relativePathStr.substring(dotIndex + 1);
                                } else if (dotIndex == 0) {
                                    ext = relativePathStr.substring(1);
                                }
                                
                                if (fileType == null || fileType.isEmpty() || fileType.equalsIgnoreCase(ext)) {
                                    objectNode.put("type", "file");
                                    objectNode.put("fileName", fileOrDirName);
                                    arrayNode.add(objectNode);
                                }
                            }
                        }
                    } catch (Exception e) {
                        if (log.isErrorEnabled()) {
                            log.error("错误的路径: {}", e.getMessage());
                        }
                    }
                });
            }

            String result = OBJECT_MAPPER.writeValueAsString(arrayNode);

            if (AIConstants.LOG_ENABLE) {
                log.info("找到{}个文件，目录 {}", arrayNode.size(), dir);
            }

            return result;
        } catch (Exception e) {
            if (AIConstants.LOG_ENABLE) {
                log.error("列出文件错误: {}", e.getMessage());
            }
            return "列出文件错误: " + e.getMessage();
        }
    }


    /**
     * 使用ripgrep工具在指定路径下搜索代码内容
     * 支持正则表达式匹配、大小写敏感控制、文件类型过滤等功能。
     *
     * @param input JSON格式的输入参数，包含以下字段：
     *              - pattern: 必填，要搜索的内容（支持正则表达式）
     *              - path: 可选，搜索路径（默认为当前目录）
     *              - fileType: 可选，文件类型过滤
     *              - caseSensitive: 可选，是否区分大小写（默认为false）
     * @return 搜索结果字符串：
     *         - 成功时返回匹配的代码行（最多显示前50条）
     *         - 失败时返回错误信息
     *         - 无匹配时返回"没有找到匹配的内容"
     */
    public static String searchContent(String input) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ContentSearchInput searchInput = mapper.readValue(input, ContentSearchInput.class);

            String pattern = searchInput.getPattern();
            String path = searchInput.getPath() != null ? searchInput.getPath() : ".";
            String fileType = searchInput.getFileType();
            boolean caseSensitive = searchInput.getCaseSensitive() != null ? searchInput.getCaseSensitive() : false;

            if (pattern == null || pattern.isEmpty()) {
                if (AIConstants.LOG_ENABLE) {
                    log.error("pattern不能为空");
                }
                return "错误: pattern不能为空";
            }

            if (AIConstants.LOG_ENABLE) {
                log.info("搜索内容: {}", pattern);
            }

            // 构建 ripgrep 命令
            List<String> args = new ArrayList<>();
            args.add(RG_PATH);
//            args.add("rg");
            args.add("--line-number");
            args.add("--with-filename");
            args.add("--color=never");

            // 是否忽略大小写
            if (!caseSensitive) {
                args.add("--ignore-case");
            }

            // 文件类型限制
            if (fileType != null && !fileType.isEmpty()) {
                args.add("--type");
                args.add(fileType);
            }

            // 搜索内容，支持正则表达式
            args.add(pattern);

            // 搜索的文件路径
            args.add(path);

            if (AIConstants.LOG_ENABLE) {
                // rg --line-number --with-filename --color=never --ignore-case What D:\myProject\github\hoppin-ai\hoppinzq-module-openai
                log.info("执行 ripgrep 的参数: {}", args);
            }

            ProcessBuilder pb = new ProcessBuilder(args);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();

            if (exitCode == 1) {
                if (AIConstants.LOG_ENABLE) {
                    log.info("没有找到匹配的内容: {}", pattern);
                }
                return "没有找到匹配的内容";
            } else if (exitCode != 0) {
                if (AIConstants.LOG_ENABLE) {
                    log.error("Ripgrep 命令执行失败: {}", exitCode);
                }
                return "错误: 搜索失败，尝试一下绝对路径";
            }

            String result = output.toString().trim();
            String[] lines = result.split("\n");

            if (AIConstants.LOG_ENABLE) {
                log.info("找到 {} 处匹配的结果: {}", lines.length, pattern);
            }

            // 限制输出以防止响应过多
            if (lines.length > 50) {
                result = String.join("\n", Arrays.copyOf(lines, 50)) +
                        String.format("\n... (展示前 50 个匹配的内容 %d)", lines.length);
            }

            return result;
        } catch (Exception e) {
            if (AIConstants.LOG_ENABLE) {
                log.error("搜索内容错误: {}", e.getMessage());
            }
            return "搜索内容错误: " + e.getMessage();
        }
    }

    /**
     * 统计子字符串在主字符串中出现的次数
     * <p>
     * 特性：对空白字符进行归一化处理，将 \r\n 统一为 \n，将制表符转换为空格，
     * 并将每行内连续多个空格压缩为单个空格，这样可以匹配由于缩进格式不同（制表符vs空格）
     * 或换行符不同（\r\n vs \n）导致的视觉上相同但技术上不同的字符串。
     *
     * @param str 主字符串，如果为null则返回0
     * @param substr 要查找的子字符串，如果为null或空字符串则返回0
     * @return 子字符串在主字符串中出现的次数
     */
    private static int countOccurrences(String str, String substr) {
        if (str == null || substr == null || substr.isEmpty()) {
            return 0;
        }

        String normalizedStr = normalizeForReplace(str);
        String normalizedSubstr = normalizeForReplace(substr);

        int count = 0;
        int idx = 0;
        while ((idx = normalizedStr.indexOf(normalizedSubstr, idx)) != -1) {
            count++;
            idx += normalizedSubstr.length();
        }
        return count;
    }

    /**
     * 归一化字符串用于替换操作
     * <p>
     * 将 \r\n 统一为 \n，将制表符转换为4个空格（保持代码缩进结构）。
     * 这样可以匹配由于缩进格式不同（制表符vs空格）或换行符不同（\r\n vs \n）导致的视觉上相同但技术上不同的字符串。
     *
     * @param str 要归一化的字符串
     * @return 归一化后的字符串
     */
    private static String normalizeForReplace(String str) {
        if (str == null) {
            return null;
        }

        // 统一换行符：将 \r\n 和 \r 都转换为 \n
        String normalized = str.replace("\r\n", "\n").replace("\r", "\n");

        // 将制表符转换为4个空格（保持代码缩进结构）
        normalized = normalized.replace("\t", "    ");

        return normalized;
    }

    /**
     * 创建新文件并写入内容，如果父目录不存在则自动创建
     *
     * @param filePath 要创建的文件路径
     * @param content 要写入文件的内容
     * @return 返回操作结果字符串，包含成功创建的文件路径
     * @throws IOException 当文件创建或写入过程中发生I/O错误时抛出
     */
    private static String createNewFile(Path filePath, String content) throws IOException {
        if (AIConstants.LOG_ENABLE) {
            log.info("创建文件: {} ({} bytes)", filePath, content.length());
        }

        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            if (AIConstants.LOG_ENABLE) {
                log.info("创建文件夹: {}", parent);
            }
            Files.createDirectories(parent);
        }

        Files.write(filePath, content.getBytes(), StandardOpenOption.CREATE);

        if (AIConstants.LOG_ENABLE) {
            log.info("成功创建文件 {}", filePath);
        }

        return "成功创建文件 " + filePath;
    }

    // ========================= teams 工具 =========================

    /**
     * spawn_teammate：启动一个 teammate 线程，进入自己的 LLM 循环。
     * <p>
     * 入参里没有显式 from，teammate 启动后用 send_message 时会通过
     * {@link #CURRENT_TEAMMATE_NAME} 自动带上自己的名字。
     */
    public static String spawnTeammate(String input) {
        try {
            if (LEAD_CLIENT == null || LEAD_MODEL == null || LEAD_BUS == null) {
                return "错误：lead client/model/bus 未初始化";
            }
            ObjectMapper mapper = new ObjectMapper();
            SpawnTeammateInput in = mapper.readValue(input, SpawnTeammateInput.class);
            if (in.getName() == null || in.getName().isBlank()) {
                return "错误：teammate name 不能为空";
            }
            List<ToolDefinition> tools = LEAD_TEAMMATE_TOOLS == null
                    ? new ArrayList<>() : new ArrayList<>(LEAD_TEAMMATE_TOOLS);
            TeammateSpawner.spawn(LEAD_CLIENT, LEAD_MODEL, in.getName(),
                    in.getRole() == null ? "assistant" : in.getRole(),
                    in.getPrompt(),
                    LEAD_BUS, tools, LEAD_REGISTRY);
            return "已启动 teammate " + in.getName();
        } catch (Exception e) {
            return "spawn_teammate 错误: " + e.getMessage();
        }
    }

    /**
     * send_message：向另一个成员发消息。lead 调用时 from=lead；
     * teammate 调用时 from=CURRENT_TEAMMATE_NAME。
     */
    @SuppressWarnings("unchecked")
    public static String sendMessage(String input) {
        try {
            if (LEAD_BUS == null) {
                return "错误：message bus 未初始化";
            }
            // teammate 工具入参可能被 invokeTool 包成 {"input":..., "tool_name":..., "__from":...}
            String fromName;
            String realInput;
            try {
                Map<String, Object> wrapped = OBJECT_MAPPER.readValue(input, Map.class);
                if (wrapped.containsKey("input") && wrapped.get("input") instanceof Map) {
                    Map<String, Object> inner = (Map<String, Object>) wrapped.get("input");
                    Object f = wrapped.get("__from");
                    fromName = f == null ? null : f.toString();
                    realInput = OBJECT_MAPPER.writeValueAsString(inner);
                } else {
                    fromName = null;
                    realInput = input;
                }
            } catch (Exception parse) {
                fromName = null;
                realInput = input;
            }
            if (fromName == null) {
                fromName = CURRENT_TEAMMATE_NAME.get();
            }
            if (fromName == null) {
                fromName = "lead";
            }
            SendMessageInput in = OBJECT_MAPPER.readValue(realInput, SendMessageInput.class);
            if (in.getTo() == null || in.getTo().isBlank()) {
                return "错误：to 不能为空";
            }
            String type = in.getType() == null ? "message" : in.getType();
            LEAD_BUS.send(fromName, in.getTo(), in.getContent() == null ? "" : in.getContent(), type);
            return "已发送消息到 " + in.getTo() + " (type=" + type + ")";
        } catch (Exception e) {
            return "send_message 错误: " + e.getMessage();
        }
    }

    /**
     * check_inbox：读取并清空 lead 自己的 inbox，返回消息摘要。
     */
    public static String checkInbox(String input) {
        try {
            if (LEAD_BUS == null) {
                return "错误：message bus 未初始化";
            }
            List<com.hoppinzq.agent.tool.bus.MailboxMessage> msgs = LEAD_BUS.readInbox("lead");
            if (msgs.isEmpty()) {
                return "inbox 为空";
            }
            StringBuilder sb = new StringBuilder();
            for (com.hoppinzq.agent.tool.bus.MailboxMessage m : msgs) {
                sb.append("- [").append(m.getType()).append("] ")
                        .append(m.getFrom()).append(": ").append(m.getContent()).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "check_inbox 错误: " + e.getMessage();
        }
    }

    // ========================= protocol 工具 =========================

    /**
     * request_shutdown：lead 向 teammate 发 shutdown_request，并在 registry 登记一条 pending 状态。
     */
    public static String requestShutdown(String input) {
        try {
            if (LEAD_BUS == null || LEAD_REGISTRY == null) {
                return "错误：bus/registry 未初始化";
            }
            ObjectMapper mapper = new ObjectMapper();
            ShutdownRequestInput in = mapper.readValue(input, ShutdownRequestInput.class);
            if (in.getTeammate() == null || in.getTeammate().isBlank()) {
                return "错误：teammate 不能为空";
            }
            String requestId = LEAD_REGISTRY.newRequestId();
            Map<String, Object> payload = new HashMap<>();
            payload.put("requestId", requestId);
            payload.put("from", "lead");
            LEAD_REGISTRY.create("shutdown_request", "lead", in.getTeammate(), payload);
            String payloadJson = OBJECT_MAPPER.writeValueAsString(payload);
            LEAD_BUS.send("lead", in.getTeammate(), payloadJson, "shutdown_request");
            return "已请求 " + in.getTeammate() + " 关闭 (requestId=" + requestId + ")";
        } catch (Exception e) {
            return "request_shutdown 错误: " + e.getMessage();
        }
    }

    /**
     * request_plan：lead 让 teammate 提交一个方案（发送一条 message 类型消息）。
     */
    public static String requestPlan(String input) {
        try {
            if (LEAD_BUS == null) {
                return "错误：message bus 未初始化";
            }
            ObjectMapper mapper = new ObjectMapper();
            RequestPlanInput in = mapper.readValue(input, RequestPlanInput.class);
            if (in.getTeammate() == null || in.getTeammate().isBlank()) {
                return "错误：teammate 不能为空";
            }
            String content = (in.getPlan() == null || in.getPlan().isBlank())
                    ? "请提交你的计划" : in.getPlan();
            LEAD_BUS.send("lead", in.getTeammate(), content, "message");
            return "已请求 " + in.getTeammate() + " 提交方案";
        } catch (Exception e) {
            return "request_plan 错误: " + e.getMessage();
        }
    }

    /**
     * review_plan：lead 对 teammate 提交的方案进行 approve/reject，
     * 并通过 mailbox 回送 plan_approval_response。
     */
    public static String reviewPlan(String input) {
        try {
            if (LEAD_BUS == null || LEAD_REGISTRY == null) {
                return "错误：bus/registry 未初始化";
            }
            ObjectMapper mapper = new ObjectMapper();
            ReviewPlanInput in = mapper.readValue(input, ReviewPlanInput.class);
            if (in.getRequestId() == null || in.getRequestId().isBlank()) {
                return "错误：requestId 不能为空";
            }
            String status = in.isApproved() ? "approved" : "rejected";
            LEAD_REGISTRY.markResponded(in.getRequestId(), status);

            // 查出原请求的发起方（teammate），把审批结果回送
            String target = "lead";
            com.hoppinzq.agent.tool.protocol.ProtocolState state =
                    LEAD_REGISTRY.get(in.getRequestId()).orElse(null);
            if (state != null && state.getSender() != null) {
                target = state.getSender();
            }
            Map<String, Object> respPayload = new HashMap<>();
            respPayload.put("requestId", in.getRequestId());
            respPayload.put("approved", in.isApproved());
            respPayload.put("comment", in.getComment() == null ? "" : in.getComment());
            LEAD_BUS.send("lead", target, OBJECT_MAPPER.writeValueAsString(respPayload),
                    "plan_approval_response");
            return "已 " + status + " 方案 requestId=" + in.getRequestId()
                    + "，并通知 " + target;
        } catch (Exception e) {
            return "review_plan 错误: " + e.getMessage();
        }
    }

    // ========================= task 工具 =========================

    public static String createTask(String input) {
        if (TASK_MANAGER == null) return "错误：任务管理器未初始化";
        try {
            TaskCreateInput in = new ObjectMapper().readValue(input, TaskCreateInput.class);
            if (in.getSubject() == null || in.getSubject().isBlank()) return "错误：subject 不能为空";
            return TASK_MANAGER.createTask(in.getSubject(), in.getDescription());
        } catch (Exception e) {
            return "create_task 错误: " + e.getMessage();
        }
    }

    public static String listTasks(String input) {
        if (TASK_MANAGER == null) return "暂无任务（任务管理器未初始化）";
        try { return TASK_MANAGER.listAllTasks(); }
        catch (Exception e) { return "list_tasks 错误: " + e.getMessage(); }
    }

    public static String getTask(String input) {
        if (TASK_MANAGER == null) return "错误：任务管理器未初始化";
        try {
            TaskGetInput in = new ObjectMapper().readValue(input, TaskGetInput.class);
            if (in.getTaskId() == null) return "错误：taskId 不能为空";
            return TASK_MANAGER.getTask(in.getTaskId());
        } catch (Exception e) { return "get_task 错误: " + e.getMessage(); }
    }

    public static String claimTask(String input) {
        if (TASK_MANAGER == null) return "错误：任务管理器未初始化";
        try {
            TaskClaimInput in = new ObjectMapper().readValue(input, TaskClaimInput.class);
            if (in.getTaskId() == null) return "错误：taskId 不能为空";
            String owner = CURRENT_TEAMMATE_NAME.get();
            if (owner == null || owner.isEmpty()) return "错误：claim_task 只能由 teammate 调用";
            String result = TASK_MANAGER.claimTask(in.getTaskId(), owner);
            // 如果任务绑定了 worktree，把当前线程的 cwd 切到 worktree 路径
            String wtName = TASK_MANAGER.getWorktree(in.getTaskId());
            if (wtName != null && !wtName.isBlank() && WORKTREE_MANAGER != null) {
                java.io.File wtDir = new java.io.File(WORKTREE_MANAGER.getWorktreesDir(), wtName);
                if (wtDir.exists()) {
                    WorktreeContext.set(wtDir.getAbsolutePath());
                    result += "\n[worktree] 已切换到 " + wtDir.getAbsolutePath();
                }
            }
            return result;
        } catch (Exception e) { return "claim_task 错误: " + e.getMessage(); }
    }

    // ========================= worktree 工具 =========================

    public static String createWorktree(String input) {
        if (WORKTREE_MANAGER == null) return "错误：worktree 管理器未初始化";
        try {
            CreateWorktreeInput in = new ObjectMapper().readValue(input, CreateWorktreeInput.class);
            if (in.getName() == null || in.getName().isBlank()) return "错误：name 不能为空";
            int taskId = in.getTaskId() == null ? 0 : in.getTaskId();
            String path = WORKTREE_MANAGER.create(in.getName(), taskId);
            return "已创建 worktree " + in.getName() + " @ " + path;
        } catch (Exception e) { return "create_worktree 错误: " + e.getMessage(); }
    }

    public static String removeWorktree(String input) {
        if (WORKTREE_MANAGER == null) return "错误：worktree 管理器未初始化";
        try {
            RemoveWorktreeInput in = new ObjectMapper().readValue(input, RemoveWorktreeInput.class);
            if (in.getName() == null || in.getName().isBlank()) return "错误：name 不能为空";
            return WORKTREE_MANAGER.remove(in.getName(), in.isDiscard());
        } catch (Exception e) { return "remove_worktree 错误: " + e.getMessage(); }
    }

    public static String keepWorktree(String input) {
        if (WORKTREE_MANAGER == null) return "错误：worktree 管理器未初始化";
        try {
            KeepWorktreeInput in = new ObjectMapper().readValue(input, KeepWorktreeInput.class);
            if (in.getName() == null || in.getName().isBlank()) return "错误：name 不能为空";
            return WORKTREE_MANAGER.keep(in.getName());
        } catch (Exception e) { return "keep_worktree 错误: " + e.getMessage(); }
    }

    public static String completeTask(String input) {
        if (TASK_MANAGER == null) return "错误：任务管理器未初始化";
        try {
            TaskClaimInput in = new ObjectMapper().readValue(input, TaskClaimInput.class);
            if (in.getTaskId() == null) return "错误：taskId 不能为空";
            return TASK_MANAGER.completeTask(in.getTaskId());
        } catch (Exception e) { return "complete_task 错误: " + e.getMessage(); }
    }
}
