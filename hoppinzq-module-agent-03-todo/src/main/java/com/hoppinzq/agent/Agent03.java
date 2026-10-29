package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
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
 * 待办事项智能体。
 *
 * <p>本类只负责<b>装配</b>：todo 工具定义、提醒钩子、/todos 命令与任务管理提示词
 * 全部收拢在 {@link TodoCapability}（capability.todo 包）。入口类不再持有
 * {@code TodoManager} 静态实例，也不再覆写任何循环方法（原 onToolExecution 提醒
 * 逻辑改由能力的 beforeLlmCall 钩子承接）。
 *
 * <p>这就是插拔式集成的完整形态：复制 {@code capability/todo/} 目录 +
 * 下面 main 中的一行 {@code agent.capability(new TodoCapability())}。
 *
 * @author hoppinzq
 */
public class Agent03 extends ZQAgent {

    public Agent03(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 基座工具（不含任何能力工具；todo 由能力注册）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, EditFileDefinition, WriteFileDefinition,
                ReadFileDefinition, GlobDefinition, ContentSearchDefinition));

        Agent03 agent = new Agent03(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/todo/ 目录 + 这一行注册
        TodoCapability todo = new TodoCapability();
        agent.capability(todo);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(buildSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", todo.promptFragments()));

        System.out.println("=== 待办事项智能体 ===");
        System.out.println(todo.description());
        System.out.println();

        // 内核 setSessionManager 需要显式给出 SkillLoader（供内置 /skills 命令使用）；
        // 03 没有技能特性，传 null 即可
        agent.setSessionManager(SessionManager.bootstrap(args), null);
        agent.run();
    }

    /**
     * 基础系统提示词（不含能力片段；todo 相关的任务管理文案由 TodoCapability.promptFragments 贡献）。
     *
     * @return 格式化的系统提示词
     */
    private static String buildSystemPrompt(String root) {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                # 角色定义
                您是一个专业的软件开发助手，专门负责代码开发、文件管理和任务协调工作。

                # 核心职责
                1. 代码开发：协助用户进行软件开发、代码编写和调试
                2. 文件操作：使用文件操作工具进行代码编辑、文件创建和内容管理
                3. 代码搜索：使用搜索工具快速定位代码片段和相关信息

                # 工作环境
                - 操作系统：%s
                - 工作目录：%s

                # 行为规范
                1. 优先使用工具：尽量使用您可用的工具来解决用户的问题
                2. 保持专注：专注于用户当前的需求，避免偏离主题
                3. 详细报告：在执行操作后，提供详细的执行结果和下一步建议

                # 工具使用指南
                您可以使用以下工具：
                - bash: 执行 Shell 命令
                - edit_file: 编辑文本文件
                - write_file: 创建或覆盖文件
                - read_file: 读取文件内容
                - glob: 查找匹配的文件和目录
                - content_search: 搜索代码内容

                # 响应格式要求
                1. 清晰简洁：使用清晰的语言描述您的操作和结果
                2. 结构化：适当使用列表、标题等结构化格式
                3. 包含上下文：在响应中引用相关的文件路径和代码片段

                请记住：您的主要目标是高效、准确地帮助用户解决问题。
                """, osName, root);
    }
}
