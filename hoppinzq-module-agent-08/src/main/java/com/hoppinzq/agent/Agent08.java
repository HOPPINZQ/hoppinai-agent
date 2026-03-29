package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.background.BackgroundManager;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import com.hoppinzq.agent.tool.task.TaskManager;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;
import static com.hoppinzq.agent.tool.background.BackgroundManager.injectBackgroundNotifications;

/**
 * 后台任务智能体
 *
 * 核心功能：
 * - 在后台线程中执行耗时命令（npm install, pytest, docker build等）
 * - 立即返回task_id，不阻塞主线程
 * - 通知队列：任务完成时将结果放入队列
 * - 在每次LLM调用前排空队列，注入后台任务结果
 *
 * 工作流程：
 * Main thread                Background thread
 * +-----------------+        +-----------------+
 * | agent loop      |        | task executes   |
 * | ...             |        | ...             |
 * | [LLM call] <---+------- | enqueue(result) |
 * |  ^drain queue   |        +-----------------+
 * +-----------------+
 *
 * Timeline:
 * Agent ----[spawn A]----[spawn B]----[other work]----
 *              |              |
 *              v              v
 *           [A runs]      [B runs]        (parallel)
 *              |              |
 *              +-- notification queue --> [results injected]
 *
 * @author hoppinzq
 */
public class Agent08 extends ZQAgent {
    public static BackgroundManager backgroundManager;
    public static TaskManager taskManager;
    public static ContextCompactor compactor;
    public static boolean manualCompactRequested = false;
    public static TodoManager todoManager;
    public static SkillLoader skillLoader;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;

    public Agent08(AnthropicClient client, String model, List<ToolDefinition> tools, BackgroundManager backgroundManager, TaskManager taskManager, ContextCompactor compactor, SkillLoader skillLoader, TodoManager todoManager) {
        super(client, model, tools);
        Agent08.backgroundManager = backgroundManager;
        Agent08.taskManager = taskManager;
        Agent08.compactor = compactor;
        Agent08.skillLoader = skillLoader;
        Agent08.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
    }

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(API_KEY)
            .baseUrl(BASE_URL)
                .build();

        backgroundManager = new BackgroundManager();
        taskManager = new TaskManager();
        compactor = new ContextCompactor(client, MODEL);
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(ReadFileDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);
        tools.add(SubAgentDefinition);
        tools.add(TodoDefinition);
        tools.add(SkillsDefinition);
        tools.add(ContentCompactDefinition);
        tools.add(TaskCreateDefinition);
        tools.add(TaskUpdateDefinition);
        tools.add(TaskListDefinition);
        tools.add(TaskGetDefinition);

        tools.add(BackgroundRunDefinition);
        tools.add(CheckBackgroundDefinition);


        Agent08 agent = new Agent08(client, MODEL, tools, backgroundManager, taskManager, compactor, skillLoader, todoManager);

        agent.setSystemPrompt(buildSystemPrompt());

        System.out.println("=== 后台任务智能体 ===");
        System.out.println("工作目录: " + ROOT);
        System.out.println("使用 background_run 工具执行耗时命令（npm install, pytest等）");
        System.out.println("命令会在后台运行，不会阻塞对话");
        System.out.println();

        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            backgroundManager.shutdown();
        }
    }

    /**
     * 构建系统提示词
     *
     * @return 系统提示词字符串
     */
    private static String buildSystemPrompt() {
        return """
                # 角色
                你是一个专业的编程智能体，具有强大的后台任务执行能力。
                
                ## 工作环境
                - 工作目录: %s
                - 操作系统: %s
                
                # 核心能力
                
                ## 文件操作
                - read_file: 读取文件内容
                - write_file: 写入文件内容
                - edit_file: 编辑文件（替换字符串）
                - list_files: 列出目录下的文件
                
                ## 代码搜索
                - content_search: 使用ripgrep搜索代码内容，支持正则表达式
                
                ## 命令执行
                - bash: 执行Shell命令（适用于快速命令）
                - background_run: 在后台执行耗时命令（不阻塞对话）
                - check_background: 检查后台任务状态
                
                ## 任务管理
                - task_create: 创建新任务
                - task_update: 更新任务状态和依赖关系
                - task_list: 列出所有任务
                - task_get: 获取任务详情
                - todo: 管理待办事项列表
                
                ## 高级功能
                - sub_agent: 委托子智能体处理复杂任务
                - load_skill: 加载特定技能
                - compact: 手动触发对话压缩
                
                # 工作指南
                
                ## 后台任务使用
                1. **识别耗时命令**: 对于预计耗时超过5秒的命令，使用background_run
                   - 常见场景: npm install, pytest, docker build, git clone, mvn build
                   - 批量测试: pytest, npm test
                   - 数据处理: 大文件转换、批量操作
                
                2. **后台任务流程**:
                   ```
                   用户请求 → background_run(command)
                            → 立即返回task_id
                            → 任务在后台执行
                            → 完成后自动注入结果到对话
                   ```
                
                3. **状态监控**:
                   - 使用check_background查看所有后台任务
                   - 后台任务完成时会自动通知
                   - 无需手动轮询，系统会自动注入结果
                
                ## 对话管理
                1. **自动压缩**: 当对话历史过长时，系统会自动压缩并保留关键信息
                2. **手动压缩**: 使用compact工具可主动触发压缩
                3. **待办提醒**: 系统会定期提醒更新待办事项
                
                ## 最佳实践
                1. **优先使用后台任务**: 避免长时间阻塞对话
                2. **合理使用任务管理**: 将大任务分解为小任务，便于跟踪进度
                3. **及时更新待办**: 保持待办事项列表的准确性
                4. **充分利用搜索**: 使用content_search快速定位代码
                
                # 重要提醒
                - 后台任务会自动通知结果，无需手动检查
                - 对话过长时会自动压缩，不用担心token限制
                - 定期更新待办事项，保持任务追踪的准确性
                - 对于复杂任务，使用sub_agent委托处理
                - 遇到问题时，使用content_search查找相关代码
                
                # 技能系统
                
                ## 可用技能
                
                %s
                
                ## 技能使用策略
                
                1. **主动学习**：遇到不熟悉的领域时，主动使用 load_skill 工具获取专业知识
                2. **何时加载技能**：遇到不熟悉的编程概念、需要遵循特定最佳实践、执行标准化工作流程时
                3. **技能加载流程**：识别知识缺口 → 使用 load_skill 加载 → 阅读理解 → 应用知识解决问题
                """.formatted(ROOT, System.getProperty("os.name"), skillLoader.getDescriptions());
    }

    /**
     * 重写chatMessage方法，集成后台任务通知和三层压缩策略
     * <p>
     * 功能流程：
     * 1. 后台通知注入：排空后台任务队列，将结果注入到对话中
     * 2. Layer 1 微压缩：每次LLM调用前自动执行，将旧工具结果替换为占位符
     * 3. Layer 2 自动压缩：当token估算超过阈值时，保存完整对话并生成摘要
     * <p>
     * 状态同步说明：
     * - ZQAgent将messageParams字段直接传递给此方法（引用传递）
     * - 微压缩返回新列表，自动压缩返回新列表
     * - 发生自动压缩时，需要同步更新ZQAgent的状态
     *
     * @param messageParams 原始消息参数列表
     * @return LLM响应消息
     */
    @Override
    protected Message chatMessage(List<MessageParam> messageParams) {
        // 步骤1: 注入后台任务通知（在压缩前执行，确保后台结果被处理）
        injectBackgroundNotifications(messageParams, backgroundManager);

        // 步骤2: Layer 1 微压缩 - 每次LLM调用前执行，静默压缩旧的工具结果
        List<MessageParam> compactedParams = compactor.microCompact(new ArrayList<>(messageParams));

        // 步骤3: Layer 2 自动压缩 - 当token估算超过阈值时触发
        if (ContextCompactor.estimateTokens(compactedParams) > TOKEN_THRESHOLD) {
            System.out.println("[自动压缩已触发]");
            compactedParams = compactor.autoCompact(compactedParams);

            // 【状态同步】需要将压缩后的消息同步到ZQAgent的messageParams字段
            // 说明：
            // - ZQAgent维护messageParams作为状态
            // - chatMessage接收的是messageParams字段的引用
            // - 但microCompact和autoCompact都返回新列表，不会修改原列表
            // - 因此需要手动将压缩后的列表同步回ZQAgent的状态

            // 更新ZQAgent的消息状态，确保下次调用使用压缩后的上下文
            this.messageParams.clear();
            this.messageParams.addAll(compactedParams);
        }

        // 使用（可能被压缩的）消息参数调用父类的chatMessage
        return super.chatMessage(compactedParams);
    }

    /**
     * 重写onToolExecution方法，实现压缩策略和待办事项提醒
     * <p>
     * 功能包括：
     * 1. 待办事项提醒：跟踪更新间隔，超过3回合未更新时提醒
     * 2. Layer 1 微压缩：每次工具执行后静默压缩
     * 3. Layer 2 自动压缩：token超阈值时触发完整压缩
     * 4. Layer 3 手动压缩：响应用户的压缩请求
     *
     * @param toolResults 工具执行结果列表
     */
    @Override
    protected void onToolExecution(List<ContentBlockParam> toolResults) {
        // ========== 待办事项提醒逻辑 ==========
        long currentVersion = todoManager.getVersion();
        if (currentVersion > lastTodoVersion) {
            // 检测到待办列表已更新，重置计数器
            roundsSinceTodo = 0;
            lastTodoVersion = currentVersion;
        } else {
            // 待办列表未更新，增加回合计数
            roundsSinceTodo++;
        }

        // 如果超过3个回合未更新待办，添加提醒消息
        if (roundsSinceTodo >= 3) {
            String reminder = String.format("<reminder>\n您已经 %d 个回合没有更新待办事项列表了。请更新列表以反映当前进度。\n</reminder>", roundsSinceTodo);
            toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(reminder)
                    .build()));
        }

        // ========== 三层压缩策略 ==========
        // Layer 1: 每次工具执行后进行微压缩（静默、频繁）
        compactor.microCompact(messageParams);

        // Layer 2: 检查是否需要自动压缩（超阈值触发）
        if (ContextCompactor.estimateTokens(messageParams) > TOKEN_THRESHOLD) {
            System.out.println("[自动压缩已触发]");
            List<MessageParam> compressed = compactor.autoCompact(messageParams);
            messageParams.clear();
            messageParams.addAll(compressed);
        }

        // Layer 3: 检查是否请求手动压缩（用户主动调用）
        if (manualCompactRequested) {
            System.out.println("[手动压缩]");
            List<MessageParam> compressed = compactor.autoCompact(messageParams);
            messageParams.clear();
            messageParams.addAll(compressed);
            manualCompactRequested = false;
        }
    }
}
