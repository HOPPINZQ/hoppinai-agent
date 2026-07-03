package com.hoppinzq.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.schema.*;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
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
     *
     * @param input
     * @return
     */
    public static String readFile(String input) {
        try {
            ReadFileInput readFileInput = OBJECT_MAPPER.readValue(input, ReadFileInput.class);

            if (LOG_ENABLE) {
                log.info("读取文件: {}", readFileInput.getPath());
            }

            Path fullPath = resolvePath(readFileInput.getPath());
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
     *
     * @param input
     * @return
     */
    public static String writeFile(String input) {
        try {
            WriteFileInput writeFileInput = OBJECT_MAPPER.readValue(input, WriteFileInput.class);

            if (LOG_ENABLE) {
                log.info("写入文件: {}", writeFileInput.getPath());
            }

            Path fullPath = resolvePath(writeFileInput.getPath());

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
     * <p>
     * 支持的命令类型：
     * - cmd: Windows CMD 命令提示符
     * - powershell: Windows PowerShell
     * - bash: Linux/Mac Bash shell（或 Windows Git Bash）
     * <p>
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
            BashInput bashInput = OBJECT_MAPPER.readValue(input, BashInput.class);

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

            // 根据命令类型和操作系统动态选择编码
            String charsetName;
            if ("bash".equalsIgnoreCase(type) ||
                (!System.getProperty("os.name").toLowerCase().contains("win") &&
                 (type == null || type.isEmpty()))) {
                charsetName = "UTF-8";
            } else {
                charsetName = "GBK";
            }

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), charsetName))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), charsetName))) {
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
     * - 当oldStr为空时直接追加newStr
     * - 当oldStr存在且唯一时执行替换
     * - 当oldStr不存在或出现多次时报错
     * 5. 将修改后的内容写回文件
     *
     * @param input JSON格式的输入字符串，包含：
     *              - path: 文件路径（必填）
     *              - oldStr: 要被替换的字符串（可选）
     *              - newStr: 要写入的新字符串（必填）
     * @return 操作结果字符串：
     * - "OK" 表示成功
     * - 错误信息字符串（如参数无效、文件读取错误等）
     */
    public static String editFile(String input) {
        try {
            EditFileInput editFileInput = OBJECT_MAPPER.readValue(input, EditFileInput.class);

            // [修复] 第一步：校验路径
            if (editFileInput.getPath() == null || editFileInput.getPath().isEmpty()) {
                if (LOG_ENABLE) {
                    log.error("编辑文件失败: 路径不能为空");
                }
                return "错误: 路径不能为空";
            }

            // [修复] 第二步：仅在 oldStr 不为 null 时检查是否与 newStr 相同
            if (editFileInput.getOldStr() != null && editFileInput.getOldStr().equals(editFileInput.getNewStr())) {
                if (LOG_ENABLE) {
                    log.error("编辑文件失败: oldStr 和 newStr 不能相同");
                }
                return "错误: oldStr 和 newStr 不能相同";
            }

            if (LOG_ENABLE) {
                log.info("编辑文件: {} (替换 {} 内容 ，新增 {} 内容)",
                        editFileInput.getPath(), editFileInput.getOldStr(), editFileInput.getNewStr());
            }

            Path filePath = resolvePath(editFileInput.getPath());
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
     * 智能路径解析：支持绝对路径和相对路径
     * <p>
     * - 绝对路径：直接使用（当 ROOT="*" 时无限制；否则需在 ROOT 范围内）
     * - 相对路径：从 ROOT 解析
     *
     * @param inputPath 用户输入的路径
     * @return 解析后的完整路径
     * @throws IllegalArgumentException 当路径超出 ROOT 限制时抛出
     */
    private static Path resolvePath(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) {
            throw new IllegalArgumentException("路径不能为空");
        }

        Path input = Paths.get(inputPath);

        // 绝对路径处理
        if (input.isAbsolute()) {
            // UNRESTRICTED_PATH_MODE=true 表示无限制模式
            if (UNRESTRICTED_PATH_MODE) {
                if (LOG_ENABLE) {
                    log.info("UNRESTRICTED_PATH_MODE=true，允许任意绝对路径: {}", inputPath);
                }
                return input.normalize();
            }

            // 普通模式：检查绝对路径是否在 ROOT 范围内
            Path rootPath = Paths.get(ROOT).toAbsolutePath();
            Path resolved = input.normalize();

            // 检查 resolved 是否以 rootPath 开头（安全沙箱）
            if (!resolved.startsWith(rootPath)) {
                throw new IllegalArgumentException(
                    "路径必须在项目目录内。ROOT=" + ROOT + "，尝试访问: " + inputPath);
            }

            if (LOG_ENABLE) {
                log.info("绝对路径（已验证在 ROOT 范围内）: {} -> {}", inputPath, resolved);
            }
            return resolved;
        }

        // 相对路径：从 ROOT 解析
        Path rootPath = Paths.get(ROOT).toAbsolutePath();
        Path resolved = rootPath.resolve(inputPath).normalize();

        // 再次检查（防止 ../ 跳出，除非在非限制模式）
        if (!UNRESTRICTED_PATH_MODE && !resolved.startsWith(rootPath)) {
            throw new IllegalArgumentException(
                "相对路径解析后超出项目目录。ROOT=" + ROOT + "，输入: " + inputPath);
        }

        if (LOG_ENABLE) {
            log.info("相对路径解析: {} -> {}", inputPath, resolved);
        }
        return resolved;
    }

    /**
     * 使用glob模式查找匹配的文件和目录
     * 类似Python的glob.glob()，支持通配符匹配文件路径
     *
     * 支持的glob模式：
     * - *: 匹配当前目录下所有文件和目录
     * - *.ext: 匹配当前目录下所有.ext文件
     * - test_*.py: 匹配以test_开头的Python文件
     *
     * @param input JSON格式的输入参数，包含pattern字段（glob匹配模式）
     * @return 匹配的文件路径列表，每行一个路径
     *         如果没有匹配则返回"(no matches)"
     *         如果发生错误则返回错误信息
     */
    public static String glob(String input) {
        try {
            GlobInput globInput = OBJECT_MAPPER.readValue(input, GlobInput.class);

            if (globInput.getPattern() == null || globInput.getPattern().isEmpty()) {
                if (LOG_ENABLE) {
                    log.error("glob pattern不能为空");
                }
                return "错误: pattern不能为空";
            }

            String pattern = globInput.getPattern();
            if (LOG_ENABLE) {
                log.info("执行glob匹配: {}", pattern);
            }

            // 确定搜索的根目录
            final Path startPath = Paths.get(ROOT).toAbsolutePath();

            // 处理glob模式中的路径部分
            final String globPattern;
            Path searchPath = startPath;
            boolean recursive = false;

            // 如果pattern包含路径分隔符，提取路径部分
            int lastSlash = pattern.lastIndexOf('/');
            if (lastSlash >= 0) {
                String pathPart = pattern.substring(0, lastSlash);
                if (!pathPart.isEmpty()) {
                    // 检查是否包含 **，表示递归搜索
                    if (pathPart.contains("**")) {
                        recursive = true;
                        // 对于 **，从根目录开始递归搜索
                        if (pathPart.equals("**")) {
                            searchPath = startPath;
                        } else {
                            // 解析相对路径（如 src/**）
                            searchPath = startPath.resolve(pathPart.replace("**", "")).normalize();
                        }
                        globPattern = pattern.substring(lastSlash + 1);
                    } else if (pathPart.equals("*")) {
                        // * 表示当前目录，特殊处理（如 */ 模式）
                        searchPath = startPath;
                        globPattern = "*";
                    } else {
                        // 普通相对路径
                        searchPath = startPath.resolve(pathPart).normalize();
                        globPattern = pattern.substring(lastSlash + 1);
                    }
                } else {
                    // pattern 以 / 开头，如 */
                    // 特殊处理：*/ 模式表示只匹配当前目录下的所有目录
                    globPattern = "*"; // 设置为匹配所有，由onlyDirectories控制只返回目录
                }
            } else {
                globPattern = pattern;
            }

            // 确保搜索路径在允许的范围内
            if (!UNRESTRICTED_PATH_MODE && !searchPath.startsWith(startPath)) {
                return "错误: 搜索路径超出允许范围";
            }

            StringBuilder results = new StringBuilder();
            int matchCount = 0;

            // 判断是否只返回目录
            boolean onlyDirectories = pattern.endsWith("/");
            // 判断是否匹配所有
            boolean matchAll = pattern.equals("*");

            try (Stream<Path> stream = recursive ? Files.walk(searchPath) : Files.list(searchPath)) {
                List<String> matches = stream
                    .filter(path -> {
                        try {
                            boolean isDirectory = Files.isDirectory(path);
                            boolean isFile = Files.isRegularFile(path);

                            // 如果pattern明确要求目录，只匹配目录
                            if (onlyDirectories) {
                                return isDirectory;
                            }

                            // 如果pattern是 *，匹配文件和目录
                            if (matchAll) {
                                return true;
                            }

                            // 其他模式（如 *.py），只匹配文件
                            if (isFile) {
                                String fileName = path.getFileName().toString();
                                // 特殊处理：globPattern是单个"*"，匹配所有文件
                                if (globPattern.equals("*")) {
                                    return true;
                                }
                                if (globPattern.startsWith("*.")) {
                                    // 处理 *.ext 模式
                                    return fileName.endsWith(globPattern.substring(1));
                                } else if (globPattern.length() > 1 && globPattern.startsWith("*") && globPattern.endsWith("*")) {
                                    // 处理 *模式* 模式（确保长度>1避免单个*的情况）
                                    String patternPart = globPattern.substring(1, globPattern.length() - 1);
                                    return fileName.contains(patternPart);
                                } else if (globPattern.startsWith("*") && globPattern.length() > 1) {
                                    // 处理 *开头模式
                                    String patternPart = globPattern.substring(1);
                                    return fileName.endsWith(patternPart);
                                } else if (globPattern.endsWith("*")) {
                                    // 处理 *结尾模式
                                    String patternPart = globPattern.substring(0, globPattern.length() - 1);
                                    return fileName.startsWith(patternPart);
                                } else {
                                    // 精确匹配
                                    return fileName.equals(globPattern);
                                }
                            }
                            return false;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(path -> {
                        try {
                            // 返回相对于ROOT的路径
                            Path relativePath = startPath.relativize(path.toAbsolutePath());
                            String result = relativePath.toString().replace("\\", "/");
                            // 如果是目录且pattern不是 */，添加/标识
                            boolean isDirectory = Files.isDirectory(path);
                            if (isDirectory && !onlyDirectories) {
                                return result + "/";
                            }
                            return result;
                        } catch (IllegalArgumentException e) {
                            // 如果路径无法相对化，返回绝对路径
                            return path.toAbsolutePath().toString().replace("\\", "/");
                        }
                    })
                    .sorted()
                    .toList();

                for (String match : matches) {
                    results.append(match).append("\n");
                    matchCount++;
                }
            }

            if (LOG_ENABLE) {
                log.info("glob匹配完成: pattern={}, 找到{}个匹配", pattern, matchCount);
            }

            if (matchCount == 0) {
                return "(no matches)";
            }

            return results.toString().trim();
        } catch (Exception e) {
            if (LOG_ENABLE) {
                log.error("glob执行错误: {}", e.getMessage());
            }
            return "glob执行错误: " + e.getMessage();
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
     * - 成功时返回匹配的代码行（最多显示前50条）
     * - 失败时返回错误信息
     * - 无匹配时返回"没有找到匹配的内容"
     */
    public static String searchContent(String input) {
        try {
            ContentSearchInput searchInput = OBJECT_MAPPER.readValue(input, ContentSearchInput.class);

            String pattern = searchInput.getPattern();
            String path = searchInput.getPath() != null ? searchInput.getPath() : ".";
            String fileType = searchInput.getFileType();
            boolean caseSensitive = searchInput.getCaseSensitive() != null ? searchInput.getCaseSensitive() : false;

            if (pattern == null || pattern.isEmpty()) {
                if (LOG_ENABLE) {
                    log.error("pattern不能为空");
                }
                return "错误: pattern不能为空";
            }

            if (LOG_ENABLE) {
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

            if (LOG_ENABLE) {
                // rg --line-number --with-filename --color=never --ignore-case What D:\myProject\github\hoppin-ai\hoppinzq-module-openai
                log.info("执行 ripgrep 的参数: {}", args);
            }

            ProcessBuilder pb = new ProcessBuilder(args);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();

            if (exitCode == 1) {
                if (LOG_ENABLE) {
                    log.info("没有找到匹配的内容: {}", pattern);
                }
                return "没有找到匹配的内容";
            } else if (exitCode != 0) {
                if (LOG_ENABLE) {
                    log.error("Ripgrep 命令执行失败: {}", exitCode);
                }
                return "错误: 搜索失败，尝试一下绝对路径";
            }

            String result = output.toString().trim();
            String[] lines = result.split("\n");

            if (LOG_ENABLE) {
                log.info("找到 {} 处匹配的结果: {}", lines.length, pattern);
            }

            // 限制输出以防止响应过多
            if (lines.length > 50) {
                result = String.join("\n", Arrays.copyOf(lines, 50)) +
                        String.format("\n... (展示前 50 个匹配的内容 %d)", lines.length);
            }

            return result;
        } catch (Exception e) {
            if (LOG_ENABLE) {
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
     * @param str    主字符串，如果为null则返回0
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
     * @param content  要写入文件的内容
     * @return 返回操作结果字符串，包含成功创建的文件路径
     * @throws IOException 当文件创建或写入过程中发生I/O错误时抛出
     */
    private static String createNewFile(Path filePath, String content) throws IOException {
        if (LOG_ENABLE) {
            log.info("创建文件: {} ({} bytes)", filePath, content.length());
        }

        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            if (LOG_ENABLE) {
                log.info("创建文件夹: {}", parent);
            }
            Files.createDirectories(parent);
        }

        Files.write(filePath, content.getBytes(), StandardOpenOption.CREATE);

        if (LOG_ENABLE) {
            log.info("成功创建文件 {}", filePath);
        }

        return "成功创建文件 " + filePath;
    }
}
