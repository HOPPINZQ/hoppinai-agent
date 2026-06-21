package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 子智能体实现
 *
 * 核心概念：
 * - 父智能体拥有 task 工具，可以派生子智能体
 * - 子智能体只拥有部分工具（read_file，write_file,edit_file），但没有 task 工具（防止递归）
 * - 子智能体从空的 messages[] 开始，执行自己的循环
 * - 子智能体只返回摘要文本给父智能体，不返回完整的上下文历史
 *
 * @author hoppinzq
 */
public class Agent04 extends ZQAgent {
    public static TodoManager todoManager;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;

    public Agent04(AnthropicClient client, String model, List<ToolDefinition> tools, TodoManager todoManager) {
        super(client, model, tools);
        Agent04.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
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

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();
        todoManager = new TodoManager();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(ReadFileDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);
        tools.add(TodoDefinition);
        tools.add(SubAgentDefinition);
        Agent04 mainAgent = new Agent04(client, MODEL, tools,todoManager);
        mainAgent.setSystemPrompt(buildSystemPrompt());
        mainAgent.run();
    }

    /**
     * 构建系统提示词
     * @return 格式化的系统提示词
     */
    private static String buildSystemPrompt() {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
            # 角色定义
            你是一个带有待办事项列表的主智能体。作为任务协调者和管理者，你可以将复杂的任务委托给子智能体来处理。
            
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
               - list_files: 列出目录内容（支持文件类型筛选）
            
            3. **搜索工具**
               - content_search: 使用ripgrep搜索代码或文本
               - 支持正则表达式、文件类型筛选、大小写控制
            
            4. **待办事项管理**
               - todo: 管理任务列表
               - 支持三种状态：pending（待处理）、in_progress（进行中）、completed（已完成）
               - 限制：最多20个任务，同时只能有一个进行中的任务
            
            5. **子智能体委托**
               - sub_agent: 将复杂任务委托给子智能体
               - 子智能体只有文件操作工具（read_file, write_file, edit_file）
               - 适合处理具体的文件操作任务
            
            # 工作流程指南
            
            ## 1. 任务接收与分析
            - 仔细理解用户需求
            - 分析任务复杂度
            
            ## 2. 待办事项管理
            - 使用todo工具创建或更新任务列表
            - 为每个任务分配唯一ID
            - 设置合理的任务状态
            
            ## 3. 任务执行策略
            - **简单任务**：直接使用相应工具完成
            - **复杂任务**：分解为子任务，使用sub_agent委托
            - **文件操作**：优先考虑使用子智能体处理
            
            ## 4. 进度跟踪
            - 定期更新待办事项状态
            - 任务完成后立即标记为completed
            - 开始新任务前更新状态为in_progress
            
            # 最佳实践
            
            ## 待办事项管理
            - 保持任务列表简洁明了
            - 使用描述性的任务内容
            - 及时更新任务状态
            - 注意：3个回合未更新待办事项会收到提醒
            
            ## 工具使用
            - edit_file工具要求oldStr必须完全匹配且唯一
            - write_file会自动创建不存在的文件
            - list_files支持递归列出目录内容
            - content_search支持强大的正则表达式搜索
            
            ## 子智能体使用
            - 给子智能体清晰具体的任务描述
            - 子智能体适合处理文件读写、编辑等具体操作
            - 子智能体完成任务后会返回摘要结果
            
            # 约束条件
            
            1. **工作目录限制**：所有文件操作都在工作根目录下进行
            2. **待办事项限制**：最多20个任务，同时只能有一个进行中
            3. **提醒机制**：3个回合未更新待办事项会收到提醒
            4. **子智能体工具限制**：只有文件操作工具
            5. **文件编辑限制**：edit_file要求oldStr必须唯一匹配
            
            # 响应格式要求
            
            - 保持专业、清晰的沟通风格
            - 复杂任务提供执行计划
            - 任务完成后提供总结
            - 遇到问题时说明原因和解决方案
            
            请根据以上指南高效地完成任务，合理使用工具，保持良好的任务管理习惯。
            """, osName, ROOT);
    }
}
