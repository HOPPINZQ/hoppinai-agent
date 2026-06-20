package com.hoppinzq.agent.tool.permission;

import java.util.Scanner;
import java.util.regex.Pattern;

import static com.hoppinzq.agent.tool.permission.PermissionRules.DENY_LIST;
import static com.hoppinzq.agent.tool.permission.PermissionRules.DESTRUCTIVE_PATTERNS;

/**
 * 三重权限闸门流水线。
 * <p>
 * 依次执行：
 * <ol>
 *   <li>den yList — 危险命令黑名单，命中即 {@link Decision.Type#DENY}</li>
 *   <li>rules    — 按工具 + 参数做策略检查（路径越界等）</li>
 *   <li>askUser  — 破坏性命令交互式确认，由调用方传入 Scanner</li>
 * </ol>
 * 任一闸门拒绝即短路返回；全部通过才 {@link Decision#allow()}。
 *
 * @author hoppinzq
 */
public class PermissionChecker {

    private final Scanner scanner;

    public PermissionChecker(Scanner scanner) {
        this.scanner = scanner;
    }

    /**
     * 对一次工具调用做权限检查。
     *
     * @param toolName  工具名
     * @param inputJson 工具输入 JSON 字符串
     * @return 决策结果（含拒绝原因或确认提示语）
     */
    public Decision check(String toolName, String inputJson) {
        // ===== 闸门 1：denyList =====
        if ("bash".equals(toolName)) {
            String command = PermissionRules.extractJsonField(inputJson, "command");
            if (command != null) {
                for (Pattern p : DENY_LIST) {
                    if (p.matcher(command).find()) {
                        return Decision.deny("命中黑名单规则 [" + p.pattern() + "]，命令被拒绝：" + command);
                    }
                }
            }
        }

        // ===== 闸门 2：rules =====
        Decision ruleDecision = PermissionRules.checkRuleBased(toolName, inputJson);
        if (ruleDecision != null) {
            return ruleDecision;
        }

        // ===== 闸门 3：askUser（破坏性命令） =====
        if ("bash".equals(toolName)) {
            String command = PermissionRules.extractJsonField(inputJson, "command");
            if (command != null) {
                for (Pattern p : DESTRUCTIVE_PATTERNS) {
                    if (p.matcher(command).find()) {
                        return askUser(command);
                    }
                }
            }
        }

        return Decision.allow();
    }

    private Decision askUser(String command) {
        if (scanner == null) {
            // 非交互环境，保守拒绝
            return Decision.deny("破坏性命令需用户确认，但当前为非交互模式：" + command);
        }
        System.out.printf("\u001b[93m[权限确认]\u001b[0m 即将执行破坏性命令：%n  %s%n允许执行吗？(y/N): ", command);
        String line = scanner.nextLine();
        if (line != null && (line.trim().equalsIgnoreCase("y") || line.trim().equalsIgnoreCase("yes"))) {
            return Decision.allow();
        }
        return Decision.deny("用户拒绝执行命令：" + command);
    }
}
