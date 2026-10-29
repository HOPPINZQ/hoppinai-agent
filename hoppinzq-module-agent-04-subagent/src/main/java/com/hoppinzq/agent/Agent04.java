package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.subagent.SubAgentCapability;
import com.hoppinzq.agent.capability.todo.TodoCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 子智能体智能体。
 *
 * <p>本类只负责<b>装配</b>：sub_agent 工具与子智能体提示词收拢在
 * {@link SubAgentCapability}（capability.subagent 包）；todo 工具、提醒钩子、
 * /todos 命令与任务管理提示词收拢在 {@link TodoCapability}（capability.todo 包）。
 * 入口类不再持有 {@code TodoManager} 静态实例，也不再覆写任何循环方法。
 *
 * <p>子智能体的执行基座（runTask / runTaskWithTokenUsage）是内核自带的，
 * 能力包只负责注册 sub_agent 工具并在 onStart 捕获宿主 Agent 以复用其 provider/model。
 *
 * <p>核心概念：
 * <ul>
 *   <li>父智能体拥有 sub_agent 工具，可以派生子智能体</li>
 *   <li>子智能体只拥有部分工具（read_file/write_file/edit_file），没有 sub_agent（防止递归）</li>
 *   <li>子智能体从空的 messages[] 开始，执行自己的循环，只把摘要返回给父智能体</li>
 * </ul>
 * 示例：AI，创建3个智能体，每个智能体独立给出剪子包袱锤，然后主智能体做裁判。直到有一个胜者
 *
 * @author hoppinzq
 */
public class Agent04 extends ZQAgent {

    public Agent04(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 基座工具（不含任何能力工具；todo / sub_agent 由能力注册）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition));

        Agent04 mainAgent = new Agent04(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/<feature>/ 目录 + 这两行注册
        TodoCapability todo = new TodoCapability();
        SubAgentCapability subAgent = new SubAgentCapability();
        mainAgent.capability(todo);
        mainAgent.capability(subAgent);

        // 基础提示 + 各能力贡献的提示片段
        mainAgent.setSystemPrompt(buildSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", todo.promptFragments())
                + "\n\n" + String.join("\n", subAgent.promptFragments()));

        System.out.println("=== 子智能体智能体 ===");
        System.out.println(todo.description());
        System.out.println(subAgent.description());
        System.out.println();

        // 内核 setSessionManager 需要显式给出 SkillLoader（供内置 /skills 命令使用）；
        // 04 没有技能特性，传 null 即可
        mainAgent.setSessionManager(SessionManager.bootstrap(args), null);
        mainAgent.run();
    }

    /**
     * 基础系统提示词（不含能力片段；todo 与子智能体的专属文案由各自能力的 promptFragments 贡献）。
     *
     * @return 格式化的系统提示词
     */
    private static String buildSystemPrompt(String root) {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
            # 角色定义
            你是主智能体，作为任务协调者和管理者，负责拆解并完成用户交给你的任务。

            # 工作环境
            你工作在 %s 操作系统，工作根目录为：%s

            # 可用工具说明

            ## 核心工具
            1. **bash** - 执行Shell命令
               - 支持cmd、powershell、bash三种类型
               - 如果不指定类型，系统会根据操作系统自动选择
               - 示例：打开网站使用 start [url]

            2. **文件操作工具**
               - read_file: 读取文件内容
               - write_file: 写入文件内容（文件不存在则创建）
               - edit_file: 编辑文件（替换指定文本）
               - glob: 列出目录内容（支持文件类型筛选）

            3. **搜索工具**
               - content_search: 使用ripgrep搜索代码或文本
               - 支持正则表达式、文件类型筛选、大小写控制

            # 工作流程指南

            ## 1. 任务接收与分析
            - 仔细理解用户需求
            - 分析任务复杂度，再决定直接执行还是拆解委派

            # 最佳实践

            ## 工具使用
            - edit_file工具要求oldStr必须完全匹配且唯一
            - write_file会自动创建不存在的文件
            - glob支持递归列出目录内容
            - content_search支持强大的正则表达式搜索

            # 约束条件

            1. **工作目录限制**：所有文件操作都在工作根目录下进行
            2. **文件编辑限制**：edit_file要求oldStr必须唯一匹配

            # 响应格式要求

            - 保持专业、清晰的沟通风格
            - 复杂任务提供执行计划
            - 任务完成后提供总结
            - 遇到问题时说明原因和解决方案

            请根据以上指南高效地完成任务。
            """, osName, root);
    }
}
