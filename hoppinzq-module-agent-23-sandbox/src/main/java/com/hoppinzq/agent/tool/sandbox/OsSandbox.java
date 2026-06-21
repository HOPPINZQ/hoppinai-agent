package com.hoppinzq.agent.tool.sandbox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * L3：OS 沙箱（Linux bubblewrap）。
 * <p>
 * 若环境为 Linux 且 PATH 中存在 {@code bwrap}，则用以下方式执行命令：
 * <pre>
 * bwrap --unshare-all --die-with-parent
 *       --bind &lt;workDir&gt; /work --ro-bind /usr /usr
 *       --proc /proc --dev /dev --tmpfs /tmp
 *       bash -c "&lt;command&gt;"
 * </pre>
 * 非 Linux 或未安装 bwrap 时 {@link #isAvailable()} 返回 false。
 *
 * @author hoppinzq
 */
public final class OsSandbox {

    private static final Boolean LINUX = System.getProperty("os.name").toLowerCase().contains("linux");
    private static final boolean AVAILABLE = LINUX && whichExists("bwrap");

    private OsSandbox() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * 用 bwrap 包裹执行命令。
     *
     * @param command 命令字符串
     * @param workDir 监禁根目录（绑定到容器内 /work，cwd 也设为 /work）
     * @param timeoutSec 超时秒数
     * @return 命令的 stdout + stderr 拼接
     */
    public static String run(String command, Path workDir, int timeoutSec) {
        List<String> argv = new ArrayList<>();
        argv.add("bwrap");
        argv.add("--unshare-all");
        argv.add("--die-with-parent");
        argv.add("--bind");
        argv.add(workDir.toAbsolutePath().toString());
        argv.add("/work");
        argv.add("--ro-bind");
        argv.add("/usr");
        argv.add("/usr");
        argv.add("--proc");
        argv.add("/proc");
        argv.add("--dev");
        argv.add("/dev");
        argv.add("--tmpfs");
        argv.add("/tmp");
        argv.add("--chdir");
        argv.add("/work");
        argv.add("bash");
        argv.add("-c");
        argv.add(command);

        return capture(argv, timeoutSec);
    }

    private static boolean whichExists(String bin) {
        try {
            Process p = new ProcessBuilder("which", bin).redirectErrorStream(true).start();
            boolean ok = p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
            return ok;
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
            return "OsSandbox 执行失败: " + e.getMessage();
        }
    }
}
