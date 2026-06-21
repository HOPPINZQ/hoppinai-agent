package com.hoppinzq.agent.tool.sandbox;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L2：目录监禁。
 * <p>
 * 从命令字符串中抽取路径 token，解析为相对 {@link #workDir} 的绝对路径，
 * 任一路径解析后不在 {@code workDir} 子树内即拒绝。
 *
 * @author hoppinzq
 */
public class DirectoryJail {

    private final Path workDir;

    /** 粗略匹配命令中的路径 token：以 / 开头、含 ./ 或 ../，或带 Windows 盘符。 */
    private static final Pattern PATH_TOKEN = Pattern.compile(
            "((?:[A-Za-z]:[\\\\/])?[^\\s'\"|;&<>]*([.][.]|[.]/|/)[^\\s'\"|;&<>]*)");

    public DirectoryJail(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    public Path getWorkDir() {
        return workDir;
    }

    /**
     * 校验命令中所有疑似路径的 token 是否都在监禁范围内。
     *
     * @return null 表示通过；非 null 为拒绝原因。
     */
    public String validate(String command) {
        if (command == null) return null;
        Matcher m = PATH_TOKEN.matcher(command);
        while (m.find()) {
            String token = m.group(1);
            // 跳过明显非文件路径的 token（如 http://...）
            if (token.startsWith("http://") || token.startsWith("https://")
                    || token.startsWith("ftp://")) {
                continue;
            }
            try {
                Path resolved = workDir.resolve(token).normalize();
                if (!resolved.startsWith(workDir)) {
                    return "路径越界: '" + token + "' 解析后为 " + resolved + "，不在监禁目录 " + workDir + " 内";
                }
            } catch (Exception e) {
                // 解析失败的 token 不阻塞执行（可能是命令选项等被误识别）
            }
        }
        return null;
    }
}
