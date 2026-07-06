package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.background.BackgroundManager;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import com.hoppinzq.agent.tool.task.TaskManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

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
    public static TodoManager todoManager;
    public static SkillLoader skillLoader;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;

    public Agent08(AnthropicClient client, String model, List<ToolDefinition> tools, BackgroundManager backgroundManager, TaskManager taskManager, SkillLoader skillLoader, TodoManager todoManager) {
        super(client, model, tools);
        Agent08.backgroundManager = backgroundManager;
        Agent08.taskManager = taskManager;
        Agent08.skillLoader = skillLoader;
        Agent08.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
    }

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(API_KEY)
            .baseUrl(BASE_URL)
            .timeout(Duration.ofSeconds(TIMEOUT))
            .maxRetries(MAX_RETRIES)
            .build();

        backgroundManager = new BackgroundManager();
        taskManager = new TaskManager();
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(ReadFileDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(GlobDefinition);
        tools.add(ContentSearchDefinition);
        tools.add(SubAgentDefinition);
        tools.add(TodoDefinition);
        tools.add(SkillsDefinition);
        tools.add(TaskCreateDefinition);
        tools.add(TaskUpdateDefinition);
        tools.add(TaskListDefinition);
        tools.add(TaskGetDefinition);
        tools.add(TaskClaimDefinition);
        tools.add(TaskCompleteDefinition);

        tools.add(BackgroundRunDefinition);
        tools.add(CheckBackgroundDefinition);


        Agent08 agent = new Agent08(client, MODEL, tools, backgroundManager, taskManager, skillLoader, todoManager);

        agent.setSystemPrompt(buildSystemPrompt());

        System.out.println("=== 后台任务智能体 ===");
        System.out.println("工作目录: " + ROOT);
        System.out.println("使用 background_run 工具执行耗时命令（npm install, pytest等）");
        System.out.println("命令会在后台运行，不会阻塞对话");
        System.out.println();

        SessionManager sessionManager = bootstrapSession(args);
        // 设置任务管理器的会话ID
        taskManager.setSessionId(sessionManager.getSessionId());

        agent.setSessionManager(sessionManager,skillLoader);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            backgroundManager.shutdown();
        }
    }

    /**
     * 启动会话：若命令行传入了 sessionId 则尝试恢复；否则交互式询问。
     * <ul>
     *   <li>{@code java AgentXX <sessionId>} —— 直接恢复指定会话</li>
     *   <li>无参启动 —— 列出已有会话，输入序号恢复或回车开新会话</li>
     * </ul>
     */
    private static SessionManager bootstrapSession(String[] args) {
        SessionManager sm = new SessionManager();
        if (args.length > 0 && !args[0].isBlank()) {
            boolean ok = sm.resume(args[0]);
            System.out.println(ok
                    ? "已恢复会话 " + args[0]
                    : "会话 " + args[0] + " 不存在或为空，将以该 ID 开始新会话");
            return sm;
        }
        Scanner sc = new Scanner(System.in);
        List<String> sessions = sm.listSessions();
        if (sessions.isEmpty()) {
            sm.startNew();
            System.out.println("新会话已创建: " + sm.getSessionId());
            return sm;
        }
        System.out.println("\n\033[95m========== 历史会话 ==========\033[0m");
        for (int i = 0; i < sessions.size(); i++) {
            System.out.printf("\033[95m%d\033[0m) %s%n", i + 1, sessions.get(i));
        }
        System.out.print("输入序号恢复对应会话，或直接回车开启新会话: ");
        String line = sc.nextLine().trim();
        if (line.isEmpty()) {
            sm.startNew();
            System.out.println("新会话已创建: " + sm.getSessionId());
            return sm;
        }
        try {
            int idx = Integer.parseInt(line) - 1;
            if (idx >= 0 && idx < sessions.size()) {
                String id = sessions.get(idx);
                sm.resume(id);
                System.out.println("已恢复会话: " + id);
                return sm;
            }
        } catch (NumberFormatException ignore) {
            // 用户可能直接输入了 sessionId
            if (sessions.contains(line)) {
                sm.resume(line);
                System.out.println("已恢复会话: " + line);
                return sm;
            }
        }
        sm.startNew();
        System.out.println("输入无效，已开启新会话: " + sm.getSessionId());
        return sm;
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
                - glob: 列出目录下的文件
                
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
    }
}
