package com.hoppinzq.agent.capability;

/**
 * 斜杠命令：能力向命令行注册的本地命令（不发送给 LLM）。
 *
 * @author hoppinzq
 */
public interface AgentCommand {

    /** 命令名，含斜杠，如 "/compact" */
    String name();

    /** 一句话说明（展示在未知命令提示中） */
    String description();

    /** 执行命令 */
    void execute();
}
