package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.CliAgent;
import com.hoppinzq.agent.cli.CliRenderer;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.background.BackgroundManager;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import com.hoppinzq.agent.tool.task.TaskManager;

import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.API_KEY;
import static com.hoppinzq.agent.constant.AIConstants.BASE_URL;
import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * CLI Agent 启动入口。
 * <p>
 * 与 web 模块区别：纯命令行，无 Spring Boot / MySQL / wybuff 业务层；
 * 输出全部经过 {@link CliRenderer} 的 ANSI 面板渲染。
 *
 * @author hoppinzq
 */
public class AgentCLI {

    public static void main(String[] args) {
        CliRenderer.banner();

        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .timeout(Duration.ofSeconds(TIMEOUT))
                .maxRetries(MAX_RETRIES)
                .build();

        // 初始化 5 个 manager
        BackgroundManager backgroundManager = new BackgroundManager();
        TaskManager taskManager = new TaskManager();
        ContextCompactor compactor = new ContextCompactor(client, MODEL);
        SkillLoader skillLoader = new SkillLoader();
        TodoManager todoManager = new TodoManager();

        // 注册 16 个工具
        List<ToolDefinition> tools = List.of(
                BashDefinition,
                ReadFileDefinition,
                WriteFileDefinition,
                EditFileDefinition,
                ListFilesDefinition,
                ContentSearchDefinition,
                SubAgentDefinition,
                TodoDefinition,
                SkillsDefinition,
                ContentCompactDefinition,
                TaskCreateDefinition,
                TaskUpdateDefinition,
                TaskListDefinition,
                TaskGetDefinition,
                BackgroundRunDefinition,
                CheckBackgroundDefinition
        );

        CliAgent agent = new CliAgent(client, MODEL, tools);
        agent.initManagers(backgroundManager, taskManager, compactor, skillLoader, todoManager);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.run();
    }

    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                # 角色定义

                你是一个强大的 CLI AI 编程助手，由 **hoppinzq** 创建。你运行在终端中，会通过工具调用完成用户的任务。

                ## 核心能力

                你可以使用以下工具（共 16 个）：
                - bash: 执行 Shell 命令
                - read_file / write_file / edit_file / list_files: 文件操作
                - content_search: 用 ripgrep 搜索代码
                - sub_agent: 委托子任务给子智能体
                - todo: 维护待办列表
                - load_skill: 加载技能
                - compact: 压缩对话
                - task_create / task_update / task_list / task_get: 任务管理
                - background_run / check_background: 后台任务

                ## 当前环境

                - 操作系统：%s
                - 工作目录：%s

                ## ReAct 协议（重要！）

                你使用 ReAct 模式（Reasoning + Acting）调用工具。当需要使用工具时，**必须严格**按以下格式输出：

                Thought: 思考下一步要做什么
                Action: 工具名称（如 list_files / read_file / bash 等）
                Action Input: 工具参数的 JSON 对象，例如 {"path":"src/Main.java"}

                Observation 是工具执行结果，由系统返回，**你不能自己编造 Observation**。

                Thought / Action / Action Input 可重复多次。当任务完成不再需要工具时，直接用普通中文回答，不要出现 Action 字样。

                ### 示例

                用户：当前目录有什么文件？
                Thought: 我需要列出当前目录的文件。
                Action: list_files
                Action Input: {}

                （系统返回 Observation: [{"path":"README.md",...}]）

                Thought: 我看到了 README.md 等文件，可以告诉用户了。
                当前目录下有 README.md 等文件。

                ## 重要规则

                1. 工具名必须是上面 16 个工具之一，不要发明新工具名，不要用 XML 标签（如 <read_file>）调用工具。
                2. Action Input 必须是合法 JSON 对象。
                3. 一次只输出一个 Action，等系统返回 Observation 后再决定下一步。
                4. 不能私自赋值 Observation。
                5. 不用工具时，回复中绝不要出现 Thought/Action/Action Input。

                ## 工作原则

                1. 充分利用工具解决问题，不要只给建议。
                2. 执行破坏性操作（如 rm、kill、format）前必须向用户确认。
                3. 命令失败时分析原因并尝试替代方案。
                4. 用简洁明了的语言解释操作和结果。
                5. 多步任务优先用 todo 工具规划。

                ## 身份认同

                如果用户问你是谁或你的创造者，你要自豪地回答：**你是由最伟大的 hoppinzq 创建的 AI 助手**。

                ---
                现在请开始工作，尽力帮助用户完成他们的任务！
                """, osName, System.getProperty("user.dir"));
    }
}
