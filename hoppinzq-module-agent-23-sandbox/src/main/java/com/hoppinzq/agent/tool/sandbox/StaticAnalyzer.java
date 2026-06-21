package com.hoppinzq.agent.tool.sandbox;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * L1：静态分析。
 * <p>
 * 用一组 regex 黑名单匹配高危命令模式，命中即拒绝。
 * 不做 AST 分析（bashlex 是 Python 库），仅用 regex 模拟。
 *
 * @author hoppinzq
 */
public final class StaticAnalyzer {

    /** 单条规则。 */
    @Data
    public static class Rule {
        private final String name;
        private final Pattern pattern;
        private final String reason;
    }

    /** 检查结果。 */
    @Data
    public static class Result {
        private final boolean blocked;
        private final String reason;
        private final String ruleName;
    }

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // rm -rf /  （绝对根目录删除）
        add("rm-rf-root", "rm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+)?(-[a-zA-Z]*r[a-zA-Z]*\\s+)?/+($|\\s)",
                "尝试递归删除根目录");
        add("rm-rf-root-2", "rm\\s+-rf\\s+/(?!tmp)", "尝试 rm -rf 非临时目录的根路径");
        // mkfs 系列文件系统格式化
        add("mkfs", "\\bmkfs(\\.[a-z0-9]+)?\\b", "尝试格式化文件系统");
        // fork bomb:  :(){ :|:& };:
        add("fork-bomb", ":\\(\\)\\s*\\{[^}]*\\}", "检测到 fork bomb 模式");
        // curl/wget pipe to sh
        add("curl-pipe-sh", "(curl|wget)[^|]*\\|\\s*(sh|bash|zsh|python)\\b",
                "curl/wget 直接管道执行 shell，远程代码执行风险");
        // dd if= ... of=/dev/sdX  写设备
        add("dd-write-device", "dd\\s+[^|]*of=/dev/(sd|nvme|hd)", "尝试用 dd 写入块设备");
        add("dd-write-device-if", "dd\\s+if=[^|]*of=/dev/", "尝试用 dd 操作块设备");
        // shutdown / reboot / halt
        add("shutdown", "\\b(shutdown|reboot|halt|poweroff|init\\s+0)\\b", "尝试关机/重启");
        // 写 /dev/sda 裸设备
        add("raw-block-write", ">\\s*/dev/(sd|nvme|hd)[a-zA-Z]*", "尝试写入裸块设备");
        // chmod -R 777 /
        add("chmod-root", "chmod\\s+-R\\s+\\S+\\s+/", "尝试对根目录递归 chmod");
        // 覆盖系统关键文件 /etc/passwd, /etc/shadow
        add("etc-passwd-overwrite", ">\\s*/etc/(passwd|shadow|sudoers)\\b", "尝试覆盖系统认证文件");
        // rm -rf /usr, /var, /boot, /lib
        add("rm-system-dirs", "rm\\s+-rf\\s+/(usr|var|boot|lib|lib64|bin|sbin|etc|root|home)(/|\\s|$)",
                "尝试 rm -rf 系统关键目录");
    }

    private StaticAnalyzer() {
    }

    private static void add(String name, String regex, String reason) {
        RULES.add(new Rule(name, Pattern.compile(regex), reason));
    }

    /**
     * 检查命令是否命中任何规则。
     *
     * @return {@link Result}：blocked=true 表示应拒绝。
     */
    public static Result check(String command) {
        if (command == null || command.isBlank()) {
            return new Result(false, null, null);
        }
        for (Rule rule : RULES) {
            if (rule.getPattern().matcher(command).find()) {
                return new Result(true, rule.getReason(), rule.getName());
            }
        }
        return new Result(false, null, null);
    }
}
