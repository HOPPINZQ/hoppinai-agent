package com.hoppinzq.agent.tool.worktree;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Git 子进程封装。统一处理 Windows/Linux 的 git 调用。
 *
 * @author hoppinzq
 */
public final class GitRunner {

    private GitRunner() {}

    /** 找到 git 可执行文件。优先 PATH 上的 git；Windows 上 git.exe 通常在 PATH 中。 */
    private static String gitBinary() {
        String onPath = System.getProperty("os.name").toLowerCase().contains("win") ? "git.exe" : "git";
        // 直接信任 PATH；调用方负责捕获报错。
        return onPath;
    }

    /**
     * 在指定 workingDir 内执行 git 子命令，返回 combined stdout+stderr。
     * 非 0 exit code 时抛出 RuntimeException，message 含输出。
     */
    public static String run(File workingDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(gitBinary());
        cmd.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workingDir != null && workingDir.exists()) {
            pb.directory(workingDir);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append("\n");
            }
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("git exit=" + code + " cmd=" + String.join(" ", cmd)
                    + " output=" + out.toString().trim());
        }
        return out.toString();
    }

    /** 找出从 startDir 向上找到的 git 仓库根目录；找不到返回 null。 */
    public static File findRepoRoot(File startDir) {
        File cur = startDir;
        while (cur != null) {
            if (new File(cur, ".git").exists()) {
                return cur;
            }
            cur = cur.getParentFile();
        }
        return null;
    }
}
