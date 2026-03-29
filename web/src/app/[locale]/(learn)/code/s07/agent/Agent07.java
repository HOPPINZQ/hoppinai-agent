package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.task.TaskManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 任务系统智能体
 *
 * 核心功能：
 * - 任务持久化到磁盘（.tasks目录）
 * - 任务依赖关系管理（blockedBy和blocks）
 * - 任务状态管理（pending -> in_progress -> completed）
 * - 依赖解析（完成任务时自动解除依赖）
 *
 * 任务图结构：
 * .tasks/
 *   task_1.json  {"id":1, "status":"completed"}
 *   task_2.json  {"id":2, "blockedBy":[1], "status":"pending"}
 *   task_3.json  {"id":3, "blockedBy":[1], "status":"pending"}
 *   task_4.json  {"id":4, "blockedBy":[2,3], "status":"pending"}
 *
 * 依赖图：
 *     task_1 (completed)
 *        ↓
 *     task_2 ←→ task_4
 *        ↓
 *     task_3 ←┘
 *
 * @author hoppinzq
 */
public class Agent07 extends ZQAgent {
    public static TaskManager taskManager;
    public static ContextCompactor compactor;
    public static boolean manualCompactRequested = false;
    public static TodoManager todoManager;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;
    public static SkillLoader skillLoader;

    public Agent07(AnthropicClient client, String model, List<ToolDefinition> tools,TaskManager taskManager, ContextCompactor compactor, SkillLoader skillLoader, TodoManager todoManager) {
        super(client, model, tools);
        Agent07.taskManager = taskManager;
        Agent07.compactor = compactor;
        Agent07.skillLoader = skillLoader;
        Agent07.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
    }

    /**
     * 重写chatMessage方法，实现两层压缩策略
     *
     * 压缩流程：
     * 1. Layer 1（微压缩）：每次LLM调用前自动执行，将旧工具结果替换为占位符
     * 2. Layer 2（自动压缩）：当token估算超过阈值时，保存完整对话并生成摘要
     *
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
        // Layer 1: 微压缩 - 每次LLM调用前执行，静默压缩旧的工具结果
        List<MessageParam> compactedParams = compactor.microCompact(new ArrayList<>(messageParams));

        // Layer 2: 自动压缩 - 当token估算超过阈值时触发
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
     *
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
        if (compactor.estimateTokens(messageParams) > TOKEN_THRESHOLD) {
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

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(API_KEY)
            .baseUrl(BASE_URL)
            .build();

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


        Agent07 agent = new Agent07(client, MODEL, tools, taskManager,compactor,skillLoader,todoManager);

        agent.setSystemPrompt(buildSystemPrompt());

        System.out.println(String.format(
                "=== 任务系统智能体 ===\n" +
                        "任务目录: %s/.tasks\n" +
                        "已有任务数: %d\n",
                System.getProperty("user.dir"),
                taskManager.getTaskCount()
        ));

        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建系统提示词
     *
     * 提示词结构：
     * 1. 身份定义 - 工作目录和角色定位
     * 2. 核心能力 - 工具和系统功能概述
     * 3. 工作原则 - 任务处理的核心准则
     * 4. 任务管理 - 任务系统使用指南
     * 5. 工具使用 - 基础工具使用规范
     * 6. 可用技能 - 动态加载的技能列表
     *
     * @return 完整的系统提示词
     */
    public static String buildSystemPrompt() {
        String skillDescriptions = skillLoader.getDescriptions();

        return """
                你是一个具有任务管理能力的编程智能体，工作在目录: %s

                ## 核心能力

                - **文件操作**：读写、编辑、搜索文件内容
                - **命令执行**：运行Shell命令和脚本
                - **任务管理**：创建、更新、查询和管理任务
                - **依赖管理**：处理任务间的前置/后置依赖关系
                - **上下文压缩**：自动管理对话上下文，支持长时间工作
                - **技能扩展**：加载专业技能以应对特定领域任务
                - **待办跟踪**：维护和更新待办事项列表

                ## 工作原则

                1. **任务优先**：接收复杂任务时，首先使用 TaskCreate 创建任务列表
                2. **依赖感知**：始终检查任务的 blockedBy 列表，只执行无依赖阻塞的任务
                3. **状态同步**：任务开始时标记为 in_progress，完成后标记为 completed
                4. **进度透明**：每完成一个任务，使用 TaskList 更新整体进度
                5. **主动更新**：定期检查并更新待办事项，反映当前工作状态

                ## 任务管理系统

                任务图结构示例：
                ```
                task_1 (completed)
                   ↓
                task_2 ←→ task_4
                   ↓
                task_3 ←┘
                ```

                ### 任务状态流转
                - **pending**：任务待执行，可能被其他任务阻塞
                - **in_progress**：任务正在执行中
                - **completed**：任务已完成，自动解除阻塞其他任务

                ### 任务工具使用指南

                1. **TaskCreate** - 创建新任务
                   - 何时使用：接收新任务、发现子任务、识别后续工作
                   - 关键字段：subject（简短标题）、description（详细说明）
                   - 依赖设置：使用 addBlockedBy 设置前置任务
                   - 示例：将"实现登录功能"拆分为"设计数据库"、"编写API"、"前端集成"

                2. **TaskUpdate** - 更新任务状态
                   - 状态转换：pending → in_progress → completed
                   - 依赖管理：使用 addBlocks/addBlockedBy 建立任务关系
                   - 重要：完成任务时自动解除依赖该任务的其他任务的阻塞

                3. **TaskList** - 查看任务全景
                   - 何时使用：开始工作前、完成任务后、迷失方向时
                   - 重点关注：status（状态）、blockedBy（阻塞关系）、owner（负责人）

                4. **TaskGet** - 获取任务详情
                   - 何时使用：需要查看任务的完整描述和依赖关系
                   - 用途：确认任务要求、检查依赖状态

                ### 任务执行策略

                - **优先级规则**：blockedBy 为空的任务优先执行
                - **并行处理**：无依赖关系的任务可以并行处理
                - **依赖跟踪**：完成任务后检查哪些任务被解除阻塞
                - **进度汇报**：每完成一个任务就调用 TaskList 查看整体进度

                ## 工具使用规范

                - **Bash**：执行Shell命令，优先使用专用工具（Grep、Glob等）
                - **ReadFile**：读取文件内容，支持多种文件格式
                - **EditFile**：编辑现有文件，精确字符串替换
                - **WriteFile**：创建新文件或完全重写现有文件
                - **ListFiles**：使用 Glob 模式查找文件
                - **ContentSearch**：使用 Grep 搜索文件内容
                - **ContentCompact**：手动触发上下文压缩

                ## 可用技能

                %s

                ## 工作流程

                1. 接收任务 → 使用 TaskCreate 分解任务
                2. TaskList 检查待办 → 确认可执行任务（无阻塞）
                3. TaskGet 查看详情 → 理解任务要求
                4. TaskUpdate(in_progress) → 开始执行
                5. 执行具体工作 → 使用工具完成任务
                6. TaskUpdate(completed) → 标记完成
                7. 返回步骤2 → 处理下一个任务
                """.formatted(ROOT, skillDescriptions);
    }
}
