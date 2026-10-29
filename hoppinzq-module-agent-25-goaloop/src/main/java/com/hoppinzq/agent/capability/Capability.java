package com.hoppinzq.agent.capability;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.List;

/**
 * 能力契约：一个可插拔的功能单元与内核之间的全部约定。
 *
 * <p>一个能力 = 一个目录（capability/&lt;name&gt;/）。集成方式：
 * <ol>
 *   <li>把能力目录复制到目标模块的 {@code com.hoppinzq.agent.capability} 包下</li>
 *   <li>在装配处加一行 {@code agent.capability(new XxxCapability(...))}</li>
 * </ol>
 * 能力对内核的依赖仅限：本契约、{@code client}（协议中立模型）、{@code session}（会话存储）、
 * {@code AIConstants.ROOT}（工作目录）。内核不反向依赖任何能力。
 *
 * <p>能力通过五种途径向内核表达自己（全部可选，按需覆盖）：
 * 工具、钩子、斜杠命令、系统提示片段、生命周期。
 *
 * @author hoppinzq
 */
public interface Capability {

    /** 能力名（小写短横线风格，如 "compact"），用于日志与诊断 */
    String name();

    /** 向 Agent 注册工具（内核会合并进工具表，同名工具以先注册者为准） */
    default List<ToolDefinition> tools() {
        return List.of();
    }

    /** 向 Agent 注册钩子，见 {@link AgentHook} 各挂载点 */
    default List<AgentHook> hooks() {
        return List.of();
    }

    /** 向命令行注册斜杠命令（如 {@code /compact}） */
    default List<AgentCommand> commands() {
        return List.of();
    }

    /** 贡献系统提示词片段（拼接在基础提示之后） */
    default List<String> promptFragments() {
        return List.of();
    }

    /** Agent 装配完成后的启动回调（如启动调度器、后台线程） */
    default void onStart(ZQAgent agent) {
    }

    /** Agent 关闭时的清理回调 */
    default void onStop() {
    }
}
