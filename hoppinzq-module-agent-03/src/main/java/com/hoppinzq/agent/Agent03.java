package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;


public class Agent03 extends ZQAgent {
    public static TodoManager todoManager;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;

    public Agent03(AnthropicClient client, String model, List<ToolDefinition> tools, TodoManager todoManager) {
        super(client, model, tools);
        Agent03.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
    }

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        todoManager = new TodoManager();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ReadFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);

        tools.add(TodoDefinition);

        Agent03 agent = new Agent03(client, MODEL, tools, todoManager);
        agent.setSystemPrompt(buildSystemPrompt());
        agent.run();
    }

    /**
     * 构建系统提示词
     *
     * @return 格式化的系统提示词
     */
    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                # 角色定义
                您是一个专业的软件开发助手，专门负责代码开发、文件管理和任务协调工作。
                您拥有一个待办事项列表系统，可以帮助您高效地管理和跟踪任务进度。
                
                # 核心职责
                1. 任务管理：使用 todo 工具来创建、更新和跟踪您的任务
                2. 代码开发：协助用户进行软件开发、代码编写和调试
                3. 文件操作：使用文件操作工具进行代码编辑、文件创建和内容管理
                4. 代码搜索：使用搜索工具快速定位代码片段和相关信息
                
                # 工作环境
                - 操作系统：%s
                - 工作目录：%s
                
                # 行为规范
                1. 优先使用工具：尽量使用您可用的工具来解决用户的问题
                2. 主动管理任务：定期更新待办事项列表，反映当前工作进度
                3. 保持专注：专注于用户当前的需求，避免偏离主题
                4. 详细报告：在执行操作后，提供详细的执行结果和下一步建议
                
                # 工具使用指南
                您可以使用以下工具：
                - bash: 执行 Shell 命令
                - edit_file: 编辑文本文件
                - write_file: 创建或覆盖文件
                - read_file: 读取文件内容
                - list_files: 列出目录内容
                - content_search: 搜索代码内容
                - todo: 管理待办事项列表
                
                # 任务管理策略
                1. 当开始新任务时，立即使用 todo 工具创建相关任务
                2. 任务进行中时，定期更新任务状态
                3. 任务完成后，标记为已完成
                4. 如果连续多个回合没有更新任务，系统会提醒您
                
                # 响应格式要求
                1. 清晰简洁：使用清晰的语言描述您的操作和结果
                2. 结构化：适当使用列表、标题等结构化格式
                3. 包含上下文：在响应中引用相关的文件路径和代码片段
                
                请记住：您的主要目标是高效、准确地帮助用户解决问题，同时保持良好的任务管理习惯。
                """, osName, ROOT);
    }

    @Override
    protected void onToolExecution(List<ContentBlockParam> toolResults) {
        long currentVersion = todoManager.getVersion();
        if (currentVersion > lastTodoVersion) {
            roundsSinceTodo = 0;
            lastTodoVersion = currentVersion;
        } else {
            roundsSinceTodo++;
        }

        if (roundsSinceTodo >= 3) {
            String reminder = String.format("""
                    <reminder>
                    您已经 %d 个回合没有更新待办事项列表了。请更新列表以反映当前进度。
                    
                    建议操作：
                    1. 检查当前任务状态
                    2. 更新已完成的任务
                    3. 添加新的任务（如果需要）
                    4. 调整任务优先级
                    </reminder>
                    """, roundsSinceTodo);
            toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(reminder)
                    .build()));
        }
    }
}
