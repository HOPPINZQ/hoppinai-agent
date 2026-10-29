package com.hoppinzq.agent.capability.mcp;

/**
 * MCP 能力的自持配置。
 * <p>特性常量不放在内核 {@code AIConstants} 里——能力被拔除时这些常量应随之消失，
 * 内核保持纯净（参见 capability-rollout-guide 第 3 节）。
 *
 * @author hoppinzq
 */
public final class McpConfig {

    private McpConfig() {
    }

    /** MCP 服务器配置文件（classpath 根），格式见 resources/mcp.json */
    public static final String MCP_CONFIG_FILE = "/mcp.json";
    /** /mcp 命令前缀：以它开头的输入都按 MCP 信息命令处理 */
    public static final String COMMAND_PREFIX = "/mcp";
}
