package com.hoppinzq.agent.tool.search;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * ripgrep 可执行文件定位器。
 *
 * <p>解析顺序（命中即缓存，全部失败返回 null 由调用方降级到纯 Java 搜索）：
 * <ol>
 *   <li>应用内置二进制（classpath:bin/ripgrep/）解压到临时目录 —— 首选，版本随应用固定</li>
 *   <li>环境变量 {@code RG_PATH} —— 内置缺失时的现场覆盖手段</li>
 *   <li>null —— 降级</li>
 * </ol>
 *
 * <p>参考 VS Code / Claude Code 的分发方式：rg 为 MIT 协议单文件可执行、零运行时依赖，
 * 允许随应用再分发。内置二进制未就位时自动走 PATH/环境变量，再不行由调用方降级，不抛异常。
 *
 * @author hoppinzq
 */
@Slf4j
public final class RipgrepLocator {

    /** 内置二进制的 ripgrep 版本号（与 resources/bin/ripgrep/ 下的文件名保持一致） */
    private static final String RG_VERSION = "15.2.0";

    /** 解压目标目录：${java.io.tmpdir}/wc-agent/rg/ */
    private static final String EXTRACT_DIR = System.getProperty("java.io.tmpdir") + java.io.File.separator + "wc-agent" + java.io.File.separator + "rg";

    /** 定位结果缓存 */
    private static volatile Path cached;
    /** 定位失败标记（避免每次搜索都重试解压） */
    private static volatile boolean failed = false;

    private RipgrepLocator() {
    }

    /**
     * 定位 ripgrep 可执行文件。结果缓存，线程安全。
     *
     * @return 可执行文件路径；全部途径失败时返回 null（调用方应降级到纯 Java 实现）
     */
    public static synchronized Path locate() {
        if (cached != null) {
            return cached;
        }
        if (failed) {
            return null;
        }
        Path found = locateByBundled();
        if (found == null) {
            found = locateByEnv();
        }
        if (found == null) {
            failed = true;
            log.warn("ripgrep 不可用（内置二进制与环境变量 RG_PATH 均未找到），内容搜索降级为纯 Java 实现");
            return null;
        }
        cached = found;
        log.info("ripgrep 已定位: {}", found);
        return found;
    }

    /** 途径一：环境变量 RG_PATH */
    private static Path locateByEnv() {
        String env = System.getenv("RG_PATH");
        if (env == null || env.isBlank()) {
            return null;
        }
        Path p = Paths.get(env);
        if (Files.isRegularFile(p) && Files.isExecutable(p)) {
            return p;
        }
        log.warn("环境变量 RG_PATH 指向的文件不存在或不可执行: {}", env);
        return null;
    }

    /** 途径二：解压应用内置二进制到临时目录 */
    private static Path locateByBundled() {
        String resource = "bin/ripgrep/rg-" + RG_VERSION + "-" + platformSuffix();
        Path target = Paths.get(EXTRACT_DIR, "rg-" + RG_VERSION + "-" + platformSuffix());
        // 已解压过且非空则复用
        if (Files.isRegularFile(target) && fileSize(target) > 0) {
            return target;
        }
        try (InputStream in = RipgrepLocator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                // 内置二进制未打包（正常现象：未按方案放入 resources 时静默降级）
                return null;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!isWindows()) {
                Files.setPosixFilePermissions(target, Set.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));
            }
            log.info("内置 ripgrep 已解压: {} -> {}", resource, target);
            return target;
        } catch (Exception e) {
            log.warn("内置 ripgrep 解压失败: {}", resource, e);
            return null;
        }
    }

    /** 按 os.name + os.arch 映射内置二进制后缀（Linux 用 musl 静态链接版规避 glibc 差异） */
    static String platformSuffix() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String archToken = arch.contains("aarch64") || arch.contains("arm64") ? "aarch64" : "x86_64";
        if (os.contains("win")) {
            return archToken + "-pc-windows-msvc.exe";
        }
        if (os.contains("linux")) {
            return archToken + "-unknown-linux-musl";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return archToken + "-apple-darwin";
        }
        return archToken + "-unknown-linux-musl";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static long fileSize(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return -1;
        }
    }
}
