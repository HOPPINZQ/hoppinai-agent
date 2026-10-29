package com.hoppinzq.agent.capability.sandbox;

/**
 * 沙箱能力自持配置。
 * <p>沙箱的层级开关（L3/L4/超时/监禁豁免）属于本能力的特性常量，
 * 随能力化移入本类：内核 {@code AIConstants} 不感知沙箱，
 * 拔除本能力目录时这些配置一并消失。
 *
 * <p>与内核 {@code Tools.resolvePath} 的 {@code UNRESTRICTED_PATH_MODE} 的关系：
 * 两者语义一致（true = 允许任意绝对路径）但相互独立——内核那份管的是基座文件工具的
 * 路径解析；沙箱这份管的是 L2 目录监禁与 beforeToolCall 路径检查是否生效。
 * 沙箱模块默认 false（监禁始终开启）。
 *
 * @author hoppinzq
 */
public final class SandboxConfig {
    private SandboxConfig() {
    }

    /** L3 OS 沙箱（Linux bwrap）：仅在 Linux 且环境中存在 bwrap 时生效 */
    public static final boolean ENABLE_OS_SANDBOX = true;

    /** L4 容器隔离（docker）：默认关闭，需要时手动开启 */
    public static final boolean ENABLE_CONTAINER = false;

    /** 单条命令执行超时（秒），超时强杀进程 */
    public static final int TIMEOUT_SEC = 30;

    /**
     * 路径限制模式：true = 允许任意路径（关闭 L2 目录监禁的 beforeToolCall 检查）；
     * false = 只允许监禁目录（ROOT）范围内的路径。
     */
    public static final boolean UNRESTRICTED_PATH_MODE = false;
}
