package com.hoppinzq.agent.capability.subagent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.session.SubAgentSessionResult;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * 子智能体能力（可整体复制的特性能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>1 个工具：{@code sub_agent}（把子任务委托给一个只带文件工具的子 ZQAgent）</li>
 *   <li>系统提示片段：子智能体的使用场景与限制</li>
 * </ul>
 *
 * <p>与旧实现的差异（为什么）：原先 {@code SubAgentDefinition} 是 {@code ToolDefinition}
 * 的静态字段、执行体在 {@code Tools.executeSubAgent}，子智能体运行所需的 provider/model
 * 靠 {@code LlmProviders.create()} 重新创建（与宿主用的不是同一个实例）；
 * 现在能力在 {@link #onStart(ZQAgent)} 捕获宿主 Agent 引用，复用宿主的 provider/model，
 * 工具定义改为 {@link #tools()} 返回的实例，工具与 {@link SubAgent} 同包后直接调用，
 * 不再经过静态类中转。
 *
 * <p>内核自带的 {@code ZQAgent.runTask/runTaskWithTokenUsage} 是子智能体的执行基座，
 * 属于内核能力，不随本能力包迁移。拔除 {@code capability/subagent/} 目录并删除装配处
 * 一行注册即可完全移除该能力。
 *
 * @author hoppinzq
 */
public class SubAgentCapability implements Capability {

    /** onStart 时捕获的宿主 Agent：复用其 provider/model，保证父子智能体走同一协议配置 */
    private ZQAgent agent;

    @Override
    public String name() {
        return "subagent";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        // 原 ToolDefinition.SubAgentDefinition 静态字段整体迁入
        ToolDefinition subAgent = new ToolDefinition(
                "sub_agent",
                "将子任务委托给子智能体处理。子智能体拥有读取文件、写入文件、编辑文件和搜索内容等工具能力，专门用于处理具体的文件操作任务。",
                ToolDefinition.createInputSchema(
                        Map.of("prompt", ToolDefinition.createProperty("string", "给子智能体的任务提示词，详细描述需要完成的任务")),
                        List.of("prompt")
                ),
                SubAgentInput.class,
                this::invokeSubAgentTool);
        return List.of(subAgent);
    }

    /**
     * sub_agent 工具执行体（原 Tools.executeSubAgent 静态方法迁入能力包）：
     * 用宿主的 provider/model 拉起子智能体，结果附加 token 使用说明后回灌。
     */
    private String invokeSubAgentTool(SubAgentInput input) {
        try {
            // 使用带 Token 统计的方法
            SubAgentSessionResult result = SubAgent.executeSubAgentWithTokenUsage(
                    agent.getProvider(), agent.getModel(), input);
            // 返回结果，附加 Token 使用信息
            return result.getResult() + "\n\n" + result.formatTokenUsage();
        } catch (Exception e) {
            return "调用子代理错误: " + e.getMessage();
        }
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "# 子智能体委托（sub_agent 能力）\n" +
                "- sub_agent: 将复杂任务委托给子智能体处理\n" +
                "- 子智能体只有文件操作工具（read_file, write_file, edit_file）与搜索工具，适合处理具体的文件操作任务\n" +
                "- 给子智能体清晰具体的任务描述；子智能体完成任务后只返回摘要结果，不会污染主上下文\n" +
                "- 单次任务最多 " + SubAgentConfig.MAX_ITERATIONS + " 轮工具循环");
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "子智能体能力：子任务委托（sub_agent），子智能体工具面 = read_file/write_file/edit_file"
                + ", 最大循环次数 = " + SubAgentConfig.MAX_ITERATIONS;
    }
}
