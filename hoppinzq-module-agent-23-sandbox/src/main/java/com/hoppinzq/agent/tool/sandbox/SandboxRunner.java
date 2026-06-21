package com.hoppinzq.agent.tool.sandbox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 沙箱编排器：按 L1 → L2 → L3 → L4 → 降级 的顺序尝试执行命令。
 * <p>
 * 使用：
 * <pre>
 * SandboxRunner runner = new SandboxRunner.Builder()
 *         .workDir(Path.of("."))
 *         .enableOsSandbox(true)
 *         .enableContainer(false)
 *         .build();
 * String result = runner.run("ls -la");
 * </pre>
 *
 * @author hoppinzq
 */
public class SandboxRunner {

    private final boolean enableStatic;     // L1 总是启用
    private final boolean enableJail;       // L2 总是启用
    private final boolean enableOsSandbox;  // L3 仅 Linux
    private final boolean enableContainer;  // L4 需 docker
    private final Path workDir;
    private final int timeoutSec;

    private SandboxRunner(Builder b) {
        this.enableStatic = true;
        this.enableJail = true;
        this.enableOsSandbox = b.enableOsSandbox;
        this.enableContainer = b.enableContainer;
        this.workDir = b.workDir;
        this.timeoutSec = b.timeoutSec;
    }

    public static class Builder {
        private Path workDir = Path.of(".").toAbsolutePath();
        private boolean enableOsSandbox = false;
        private boolean enableContainer = false;
        private int timeoutSec = 30;

        public Builder workDir(Path p) {
            this.workDir = p;
            return this;
        }

        public Builder enableOsSandbox(boolean v) {
            this.enableOsSandbox = v;
            return this;
        }

        public Builder enableContainer(boolean v) {
            this.enableContainer = v;
            return this;
        }

        public Builder timeoutSec(int s) {
            this.timeoutSec = s;
            return this;
        }

        public SandboxRunner build() {
            return new SandboxRunner(this);
        }
    }

    /**
     * 执行命令，按 L1→L4→降级 顺序。
     */
    public String run(String command) {
        if (command == null || command.isBlank()) {
            return "❌ 空命令";
        }

        // L1：静态分析（总是启用）
        if (enableStatic) {
            StaticAnalyzer.Result sr = StaticAnalyzer.check(command);
            if (sr.isBlocked()) {
                return "❌ 命令被静态分析拒绝 [" + sr.getRuleName() + "]: " + sr.getReason();
            }
        }

        // L2：目录监禁（总是启用）
        if (enableJail) {
            DirectoryJail jail = new DirectoryJail(workDir);
            String reject = jail.validate(command);
            if (reject != null) {
                return "❌ 路径越界，被目录监禁拒绝: " + reject;
            }
        }

        // L3：OS 沙箱（可选）
        if (enableOsSandbox && OsSandbox.isAvailable()) {
            return "[L3 bwrap]\n" + OsSandbox.run(command, workDir, timeoutSec);
        }

        // L4：容器隔离（可选）
        if (enableContainer && ContainerIsolation.isAvailable()) {
            return "[L4 docker]\n" + ContainerIsolation.run(command, workDir, timeoutSec);
        }

        // 降级：带超时的 ProcessBuilder，cwd 锁在 workDir
        return "[降级 ProcessBuilder, cwd=" + workDir + "]\n" + runWithTimeout(command, workDir, timeoutSec);
    }

    /** 降级执行：用系统默认 shell，cwd 锁在 workDir，超时强杀。 */
    static String runWithTimeout(String command, Path workDir, int timeoutSec) {
        ProcessBuilder pb;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("bash", "-c", command);
        }
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        try {
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "GBK"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append("\n");
                }
            }
            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return out.toString().trim() + "\n[超时]";
            }
            if (p.exitValue() != 0) {
                return out.toString().trim() + "\n[exitCode=" + p.exitValue() + "]";
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "执行失败: " + e.getMessage();
        }
    }
}
