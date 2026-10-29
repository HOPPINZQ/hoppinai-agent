package com.hoppinzq.agent.capability.hooks;

import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;

import java.util.ArrayList;
import java.util.List;

/**
 * 钩子示例能力（可整体复制的能力包）。
 *
 * <p>本模块演示的重点不是某个工具，而是<b>钩子挂载点本身</b>。原实现自建了
 * HookEvent / HookCallback / HookRegistry 一套框架（4 类事件 + 注册表），
 * 与内核的 {@link AgentHook} 契约重复；能力化后直接改用内核契约，自建框架删除。
 *
 * <p>四个示例钩子（全部是 {@link AgentHook} 实现，按注册顺序执行）：
 * <ul>
 *   <li>{@link DangerousCommandHook} → {@code beforeToolCall}，拦截危险命令</li>
 *   <li>{@link AuditLogHook}         → {@code beforeToolCall}/{@code afterToolCall}，审计日志</li>
 *   <li>{@link LargeOutputHook}      → {@code afterToolCall}，输出过长告警</li>
 *   <li>{@link TurnSummaryHook}      → {@code afterToolCall}/{@code afterLlmResponse}，每轮工具调用数统计</li>
 * </ul>
 *
 * <p>对内核的依赖仅限：Capability / AgentHook 契约。
 *
 * @author hoppinzq
 */
public class HooksCapability implements Capability {

    private final List<AgentHook> hooks = new ArrayList<>();

    public HooksCapability() {
        hooks.add(new DangerousCommandHook());
        hooks.add(new AuditLogHook());
        hooks.add(new LargeOutputHook());
        hooks.add(new TurnSummaryHook());
    }

    @Override
    public String name() {
        return "hooks";
    }

    @Override
    public List<AgentHook> hooks() {
        return List.copyOf(hooks);
    }

    @Override
    public List<String> promptFragments() {
        return List.of("""
                ## 钩子规则
                - bash 工具会经过危险命令护栏：rm -rf /、sudo、格式化盘符等命令会被直接拦截并返回拒绝说明；
                  收到拦截结果时不要重试同样的命令，改用安全的等价操作或向用户说明。""");
    }

    /** 启动横幅描述 */
    public String description() {
        return "已注册 4 个钩子：DangerousCommandHook(拦截) / AuditLogHook(审计) / "
                + "LargeOutputHook(长输出告警) / TurnSummaryHook(每轮统计)";
    }
}