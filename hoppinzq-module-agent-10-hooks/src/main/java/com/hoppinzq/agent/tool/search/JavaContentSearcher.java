package com.hoppinzq.agent.tool.search;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 纯 Java 内容搜索器：ripgrep 不可用时的兜底实现（{@link java.nio.file.Files#walk} + 正则）。
 *
 * <p>输出格式与 rg（--line-number --with-filename --color=never）对齐：{@code 路径:行号:内容}，
 * 保证 LLM 侧提示词与解析逻辑无需感知引擎差异。
 *
 * @author hoppinzq
 */
@Slf4j
public final class JavaContentSearcher {

    /** 跳过的目录名 */
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".svn", ".idea", "node_modules", "target", "build", "dist", ".gradle", ".mvn", "out");
    /** 跳过的文件后缀（二进制/大型产物） */
    private static final Set<String> SKIP_SUFFIXES = Set.of(
            ".jar", ".class", ".exe", ".dll", ".so", ".dylib", ".zip", ".gz", ".tar", ".7z", ".rar",
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".pdf", ".woff", ".woff2", ".ttf", ".mp3", ".mp4");
    /** 单文件大小上限（超过跳过） */
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    /** 遍历文件数上限（防超大仓库拖死） */
    private static final int MAX_FILES = 5000;
    /** 结果行数上限（与 rg 路径的展示上限一致） */
    private static final int MAX_RESULTS = 50;

    /** rg --type 参数 → 文件后缀 的常用映射（rg 的 --type 语义在此收窄为后缀过滤） */
    private static final Map<String, List<String>> TYPE_SUFFIXES = Map.ofEntries(
            Map.entry("java", List.of(".java")),
            Map.entry("py", List.of(".py")),
            Map.entry("js", List.of(".js", ".jsx", ".mjs", ".cjs")),
            Map.entry("ts", List.of(".ts", ".tsx")),
            Map.entry("md", List.of(".md")),
            Map.entry("txt", List.of(".txt")),
            Map.entry("xml", List.of(".xml")),
            Map.entry("yaml", List.of(".yaml", ".yml")),
            Map.entry("json", List.of(".json")),
            Map.entry("sql", List.of(".sql")),
            Map.entry("html", List.of(".html", ".htm")),
            Map.entry("css", List.of(".css", ".scss", ".less")),
            Map.entry("rust", List.of(".rs")),
            Map.entry("go", List.of(".go")),
            Map.entry("c", List.of(".c", ".h")),
            Map.entry("cpp", List.of(".cpp", ".hpp", ".cc", ".hh")));

    private JavaContentSearcher() {
    }

    /**
     * 执行搜索。
     *
     * @param pattern       正则表达式
     * @param path          搜索的文件或目录
     * @param fileType      可选文件类型（rg --type 语义的近似：常用类型白名单，其余按后缀处理）
     * @param caseSensitive 是否区分大小写
     * @return rg 格式的匹配行（最多 50 行）；无匹配或出错时返回说明文本
     */
    public static String search(String pattern, String path, String fileType, boolean caseSensitive) {
        Path root = Paths.get(path);
        if (!Files.exists(root)) {
            return "错误: 路径不存在: " + path;
        }
        Pattern regex;
        try {
            regex = caseSensitive ? Pattern.compile(pattern)
                    : Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (Exception e) {
            return "错误: 无效的正则表达式: " + e.getMessage();
        }
        List<String> suffixes = resolveSuffixes(fileType);

        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !isSkipped(p, suffixes))
                    .limit(MAX_FILES)
                    .toList();
        } catch (Exception e) {
            log.warn("纯 Java 搜索遍历失败: {}", path, e);
            return "错误: 遍历路径失败: " + e.getMessage();
        }

        List<String> results = new ArrayList<>();
        AtomicInteger matchedCount = new AtomicInteger(0);
        AtomicBoolean enough = new AtomicBoolean(false);
        // 并行扫描：结果顺序无保障（rg 本身也不保证跨文件顺序），命中上限即停
        files.parallelStream().takeWhile(p -> !enough.get()).forEach(p -> {
            scanFile(p, regex, results, matchedCount, enough);
        });

        if (results.isEmpty()) {
            return "没有找到匹配的内容";
        }
        StringBuilder sb = new StringBuilder(String.join("\n", results));
        if (enough.get()) {
            sb.append("\n... (结果过多，仅展示前 ").append(MAX_RESULTS).append(" 行，共匹配约 ").append(matchedCount.get()).append(" 处)");
        }
        return sb.toString();
    }

    /** 单文件逐行扫描，命中写入 results（超上限置 enough 停止其他线程） */
    private static void scanFile(Path file, Pattern regex, List<String> results,
                                 AtomicInteger matchedCount, AtomicBoolean enough) {
        if (enough.get() || isBinary(file)) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (regex.matcher(line).find()) {
                    int total = matchedCount.incrementAndGet();
                    if (results.size() < MAX_RESULTS) {
                        // rg 格式：路径:行号:内容
                        results.add(file + ":" + lineNo + ":" + line);
                    }
                    if (total >= MAX_RESULTS * 4) {
                        // 多命中一些再停，保证"共匹配约 N 处"的数字有参考性
                        enough.set(true);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // 单文件失败（编码/权限等）静默跳过，与 rg 行为一致
        }
    }

    /** 文件头 8KB 含 \0 判定为二进制，跳过 */
    private static boolean isBinary(Path file) {
        try (var in = Files.newInputStream(file)) {
            byte[] head = new byte[8192];
            int read = in.readNBytes(head, 0, head.length);
            for (int i = 0; i < read; i++) {
                if (head[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true; // 读不了按二进制处理
        }
    }

    private static boolean isSkipped(Path file, List<String> suffixes) {
        Path name = file.getFileName();
        if (name == null) {
            return true;
        }
        String fileName = name.toString().toLowerCase();
        for (String suffix : SKIP_SUFFIXES) {
            if (fileName.endsWith(suffix)) {
                return true;
            }
        }
        // 父目录链上命中跳过目录则排除
        for (Path part : file) {
            if (SKIP_DIRS.contains(part.toString())) {
                return true;
            }
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return true;
            }
        } catch (Exception e) {
            return true;
        }
        // 文件类型过滤：白名单映射，非白名单按 .<fileType> 后缀处理
        if (suffixes != null) {
            boolean hit = false;
            for (String suffix : suffixes) {
                if (fileName.endsWith(suffix)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return true;
            }
        }
        return false;
    }

    /** rg --type 参数 → 后缀列表；null 表示不过滤 */
    private static List<String> resolveSuffixes(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return null;
        }
        String key = fileType.trim().toLowerCase();
        List<String> mapped = TYPE_SUFFIXES.get(key);
        if (mapped != null) {
            return mapped;
        }
        String suffix = key.startsWith(".") ? key : "." + key;
        return List.of(suffix);
    }
}
