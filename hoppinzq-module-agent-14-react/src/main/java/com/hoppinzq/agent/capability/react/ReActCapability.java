package com.hoppinzq.agent.capability.react;

import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.List;

import static com.hoppinzq.agent.capability.react.ReactConfig.REACT_ENABLE;

/**
 * ReAct（推理 + 行动）能力（可整体复制的能力包）。
 *
 * <p>ReAct 在本框架里不是"另一种主循环"，而是<b>提示词约束</b>：模型被要求按
 * {@code Thought / Action / Action Input} 三行固定格式输出，工具调用仍由内核的
 * 标准 tool_calls 通道执行。因此本能力只贡献系统提示片段，不注册工具、不挂钩子。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>{@link #promptFragments()} —— ReAct 格式约束（含可用工具名集合，防止模型臆造工具）</li>
 *   <li>{@link #normalPrompt()} —— 关闭 ReAct 时的普通提示（{@link ReactConfig#REACT_ENABLE}）</li>
 * </ul>
 *
 * <p>对内核的依赖仅限：Capability 契约、ToolDefinition、AIConstants.ROOT。
 *
 * @author hoppinzq
 */
public class ReActCapability implements Capability {

    /** 本 Agent 注册的工具（用于渲染可用工具清单与工具名集合） */
    private final List<ToolDefinition> tools;

    public ReActCapability(List<ToolDefinition> tools) {
        this.tools = tools;
    }

    @Override
    public String name() {
        return "react";
    }

    @Override
    public List<String> promptFragments() {
        return List.of(isEnabled() ? buildReActSystemPrompt() : buildNormalSystemPrompt());
    }

    /** 当前是否启用 ReAct 提示词 */
    public boolean isEnabled() {
        return REACT_ENABLE;
    }

    /** 启动横幅描述 */
    public String description() {
        return isEnabled()
                ? "ReAct 模式：模型按 Thought / Action / Action Input 三行格式推理并调用工具"
                : "普通模式：直接调用工具，无 ReAct 格式约束";
    }

    /**
     * 构建普通模式系统提示词。
     */
    private String buildNormalSystemPrompt() {
        StringBuilder toolDescriptions = new StringBuilder();
        for (ToolDefinition tool : tools) {
            toolDescriptions.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            try {
                toolDescriptions.append("  参数 Schema: ")
                        .append(AIConstants.OBJECT_MAPPER.writeValueAsString(tool.getInputSchema())).append("\n");
            } catch (Exception ignored) {
            }
        }
        return String.format("""
            你是一个专业的编程智能体，具有强大的后台任务执行能力。你可以使用工具来帮助用户解决编程和系统任务。

            ## 可用工具

            %s

            ## 工作目录

            你工作在 %s 目录下。

            请记住：你的目标是帮助用户高效地完成任务。如果需要使用工具，请直接调用。
            """, toolDescriptions, AIConstants.ROOT);
    }

    /**
     * 构建 ReAct 系统提示词。
     */
    private String buildReActSystemPrompt() {
        StringBuilder toolDescriptions = new StringBuilder();
        for (ToolDefinition tool : tools) {
            toolDescriptions.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            try {
                toolDescriptions.append("  参数 Schema: ")
                        .append(AIConstants.OBJECT_MAPPER.writeValueAsString(tool.getInputSchema())).append("\n");
            } catch (Exception ignored) {
            }
        }

        String actionNames = tools.stream().map(ToolDefinition::getName).reduce((a, b) -> a + "," + b).orElse("");

        return String.format("""
            你是一个专业的编程智能体，使用 ReAct 模式（推理+行动）解决复杂问题。

            ========== 输出格式（必须 100%% 严格遵守，违反即任务失败） ==========

            当需要使用工具时，**单次回复**只能按下面三行的原文格式输出（顺序固定、关键字固定、冒号后有一个空格）：

            Thought: 你的思考内容
            Action: 工具名
            Action Input: JSON参数

            不需要使用工具时，直接用自然语言回答用户，回复中**绝不能**出现 Thought / Action / Action Input / Observation 这些关键字。

            ---------- 以下写法都属于格式错误，严禁出现 ----------

            [错] <Action: glob>          ← 禁止用尖括号 <> 包裹
            [错] <Action Input: {}>            ← 禁止用尖括号 <> 包裹
            [错] <Thought: ...>                ← 禁止用尖括号 <> 包裹
            [错] **Action**: glob        ← 禁止用 markdown 加粗
            [错] `Action: glob`          ← 禁止用反引号包裹
            [错] ```Action: glob```      ← 禁止用代码块包裹
            [错] Action：glob            ← 禁止用中文冒号 ：
            [错] Action:glob             ← 冒号后必须有一个空格
            [错] 一次回复出现多组 Thought/Action/Action Input
            [错] 自己编造 Observation 的值

            ---------- 唯一正确写法 ----------

            Thought: 用户想要查看当前目录的文件列表，应使用 glob 工具
            Action: glob
            Action Input: {}

            ========== 工作流程 ==========

            1. 收到用户问题，判断是否需要工具
            2. 需要工具：输出一组 Thought/Action/Action Input 后立即停止，等待系统返回 Observation
            3. 收到 Observation 后，根据结果继续思考下一步（如需再次调用工具，重复步骤 2）
            4. 不再需要工具：用自然语言直接回答用户（此时不要输出 Thought/Action/Action Input）
            5. 缺少必要参数时：直接向用户提问，不要调用工具
            6. 工具执行出错时：向用户说明情况并寻求帮助

            ========== 完整对话示例 ==========

            用户: 请列出当前目录的文件

            AI:
            Thought: 用户想要查看当前目录的文件列表，应使用 glob 工具
            Action: glob
            Action Input: {}

            （系统执行后返回）Observation: ["file1.txt", "file2.java"]

            AI: 我找到了以下文件：file1.txt, file2.java

            ========== 关键约束 ==========

            1. 工具名只能是以下集合中的一个：[%s]
            2. Action Input 必须是合法 JSON 对象，以 { 开头、} 结尾
            3. 一次回复只能有一组 Thought/Action/Action Input，等待 Observation 才能继续
            4. Observation 永远由系统返回，**绝对不能**自己填写
            5. 同一工具需要多次调用时，每轮都完整输出 Thought/Action/Action Input

            ## 可用工具

            %s

            ## 工作目录

            你工作在 %s 目录下。

            请记住：输出格式错误（例如使用尖括号、加粗、中文冒号、自编 Observation）会导致整个任务失败。
            """, actionNames, toolDescriptions, AIConstants.ROOT);
    }
}