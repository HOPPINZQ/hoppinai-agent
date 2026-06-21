package com.hoppinzq.agent.tool.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 完整的文件排除工具类，用于判断给定的文件路径是否应该被排除
 *
 * @author deepseek
 */
public class FileExclusionHelper {
    private final Set<String> excludedDirs;
    private final Set<Pattern> excludedFilePatterns;

    public FileExclusionHelper(Set<String> excludedDirs, Set<String> filePatterns) {
        this.excludedDirs = excludedDirs;
        this.excludedFilePatterns = compilePatterns(filePatterns);
    }

    private Set<Pattern> compilePatterns(Set<String> wildcardPatterns) {
        Set<Pattern> patterns = new HashSet<>();
        for (String wildcard : wildcardPatterns) {
            patterns.add(compileWildcardToPattern(wildcard));
        }
        return patterns;
    }

    private Pattern compileWildcardToPattern(String wildcard) {
        // 将路径分隔符统一为 /
        String normalized = wildcard.replace("\\", "/");

        StringBuilder regex = new StringBuilder();
        regex.append("^");

        String[] parts = normalized.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append("/");
            }

            String part = parts[i];
            if ("**".equals(part)) {
                regex.append(".*");
            } else {
                // 转义特殊字符
                part = part.replace(".", "\\.")
                        .replace("+", "\\+")
                        .replace("$", "\\$")
                        .replace("[", "\\[")
                        .replace("]", "\\]")
                        .replace("(", "\\(")
                        .replace(")", "\\)");

                // 将 * 替换为 [^/]*
                part = part.replace("*", "[^/]*");
                // 将 ? 替换为 [^/]
                part = part.replace("?", "[^/]");

                regex.append(part);
            }
        }

        // 如果以 ** 结尾，需要特殊处理
        if (wildcard.endsWith("/**")) {
            regex.append(".*");
        }

        regex.append("$");

        return Pattern.compile(regex.toString());
    }

    public boolean shouldExclude(String relativePathStr, boolean isDirectory) {
        // 统一路径分隔符
        String normalizedPath = relativePathStr.replace("\\", "/");

        // 检查目录排除
        boolean dirExcluded = excludedDirs.stream()
                .anyMatch(excludedDir -> {
                    if (normalizedPath.equals(excludedDir)) {
                        return true; // 完全匹配目录名
                    }
                    if (normalizedPath.startsWith(excludedDir + "/")) {
                        return true; // 路径以排除目录开头
                    }
                    return normalizedPath.contains("/" + excludedDir + "/"); // 路径中包含排除目录
                });

        if (dirExcluded) {
            return true;
        }

        // 如果是目录，只检查目录排除规则，不检查文件模式
        if (isDirectory) {
            return false;
        }

        // 检查文件模式排除（只对文件生效）
        for (Pattern pattern : excludedFilePatterns) {
            if (pattern.matcher(normalizedPath).matches()) {
                return true;
            }
        }

        return false;
    }
}