package com.hoppinzq.agent.tool.permission;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static com.hoppinzq.agent.constant.AIConstants.ROOT;

/**
 * 权限规则表（静态）。
 * <p>
 * 第一闸门 {@link #DENY_LIST}：硬编码的危险命令黑名单（按正则匹配 bash 的 command 字符串）。
 * 第二闸门 {@link #checkRuleBased(String, String)}：按工具名 + 参数做更细致的策略，
 *   例如对 write_file/edit_file/read_file 做路径越界检查（必须在 ROOT 下）。
 *
 * <p>第三闸门 ASK 不放在这里 —— 由 {@link PermissionChecker} 在前两闸门都通过后，
 * 对破坏性命令触发交互式确认。
 *
 * @author hoppinzq
 */
public final class PermissionRules {

    private PermissionRules() {
    }

    /**
     * 危险命令黑名单（不区分大小写）。
     * 任何一条匹配，立即返回 DENY。
     */
    public static final List<Pattern> DENY_LIST = List.of(
            // 经典删库
            Pattern.compile("rm\\s+-rf\\s+/(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("rm\\s+-rf\\s+~", Pattern.CASE_INSENSITIVE),
            Pattern.compile("rm\\s+-rf\\s+\\*", Pattern.CASE_INSENSITIVE),
            // 提权
            Pattern.compile("(^|\\s)sudo(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)su(\\s|$)", Pattern.CASE_INSENSITIVE),
            // Windows 强删
            Pattern.compile("del\\s+/[a-z]*f[a-z]*\\s+/[a-z]*s[a-z]*\\s+/q\\s+C:\\\\", Pattern.CASE_INSENSITIVE),
            Pattern.compile("rd\\s+/s\\s+/q\\s+C:\\\\", Pattern.CASE_INSENSITIVE),
            // 格式化
            Pattern.compile("format\\s+[A-Z]:", Pattern.CASE_INSENSITIVE),
            // fork 炸弹 / 网络外发破坏
            Pattern.compile(":\\(\\)\\s*\\{.*\\};", Pattern.CASE_INSENSITIVE),
            // 写设备节点
            Pattern.compile("dd\\s+if=.*of=/dev/(sd|nvme|hd)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 破坏性命令白名单 —— 即便未被 DENY_LIST 命中，也需用户确认才能执行。
     * （第三闸门的判断依据）
     */
    public static final List<Pattern> DESTRUCTIVE_PATTERNS = List.of(
            Pattern.compile("\\brm\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\brmdir\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdel\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgit\\s+push\\s+.*--force", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgit\\s+reset\\s+--hard", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmv\\s+\\S+\\s+/dev/null", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 第二闸门：按工具名 + 参数做规则匹配。
     * 这里只实现一条核心规则：文件工具的路径不能越出 ROOT。
     *
     * @param toolName 工具名
     * @param inputJson 工具输入 JSON 字符串
     * @return 命中规则返回 Decision.deny(reason)；未命中返回 null（让流水线下一步继续）
     */
    public static Decision checkRuleBased(String toolName, String inputJson) {
        switch (toolName) {
            case "read_file", "write_file", "edit_file" -> {
                String pathStr = extractJsonField(inputJson, "path");
                if (pathStr == null || pathStr.isEmpty()) {
                    return null;
                }
                if (isPathEscape(pathStr)) {
                    return Decision.deny("路径越界：" + pathStr + " 不在工作目录 " + ROOT + " 内");
                }
            }
            default -> {
                // 其它工具不做规则检查
            }
        }
        return null;
    }

    /**
     * 判断 pathStr 解析后是否仍在 ROOT 之内。
     */
    public static boolean isPathEscape(String pathStr) {
        try {
            Path root = Paths.get(ROOT).toAbsolutePath().normalize();
            Path target = root.resolve(pathStr).normalize();
            return !target.startsWith(root);
        } catch (Exception e) {
            // 解析异常时保守视为越界
            return true;
        }
    }

    /** 极简 JSON 字段抽取，避免对完整 POJO 的依赖 */
    static String extractJsonField(String json, String field) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t");
        }
        return null;
    }
}
