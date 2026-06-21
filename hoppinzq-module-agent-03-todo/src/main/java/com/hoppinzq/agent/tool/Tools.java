package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.schema.*;
import com.hoppinzq.agent.tool.util.FileExclusionHelper;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * @author hoppinzq
 */
@Slf4j
public class Tools {

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
            Path currentPath = Path.of(ROOT);
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
            Path currentPath = Path.of(ROOT);
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

            Path filePath = Paths.get(ROOT+File.separator+editFileInput.getPath());
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

                newContent = oldContent.replace(editFileInput.getOldStr(), editFileInput.getNewStr());
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

            String dir = ROOT;
            String fileType;
            if (listFilesInput.getPath() != null && !listFilesInput.getPath().isEmpty()) {
                dir = ROOT + File.separator + listFilesInput.getPath();
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
}
