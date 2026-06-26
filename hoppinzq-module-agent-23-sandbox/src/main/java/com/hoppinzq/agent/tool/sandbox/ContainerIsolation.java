package com.hoppinzq.agent.tool.sandbox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * L4：容器隔离（docker）。
 * <p>
 * 若本机存在可用 docker daemon，则用以下方式执行命令：
 * <pre>
 * docker run --rm --network=none --read-only --memory=512m
 *            -v &lt;workDir&gt;:/work -w /work
 *            ubuntu:22.04 bash -c "&lt;command&gt;"
 * </pre>
 * daemon 不可用时 {@link #isAvailable()} 返回 false。
 *
 * @author hoppinzq
 */
public final class ContainerIsolation {

    private static final boolean AVAILABLE = checkDocker();

    private ContainerIsolation() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String run(String command, Path workDir, int timeoutSec) {
        List<String> argv = new ArrayList<>();
        argv.add("docker");
        argv.add("run");
        argv.add("--rm");
        argv.add("--network=none");
        argv.add("--read-only");
        argv.add("--memory=512m");
        argv.add("--cpus=1");
        argv.add("-v");
        argv.add(workDir.toAbsolutePath() + ":/work");
        argv.add("-w");
        argv.add("/work");
        argv.add("ubuntu:22.04");
        argv.add("bash");
        argv.add("-c");
        argv.add(command);

        return capture(argv, timeoutSec);
    }

    private static boolean checkDocker() {
        try {
            Process p = new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String capture(List<String> argv, int timeoutSec) {
        try {
            Process p = new ProcessBuilder(argv).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append("\n");
                }
            }
            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return out + "\n[超时]";
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "ContainerIsolation 执行失败: " + e.getMessage();
        }
    }
}
