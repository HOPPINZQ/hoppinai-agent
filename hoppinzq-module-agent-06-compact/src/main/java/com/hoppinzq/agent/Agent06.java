package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.time.Duration;
import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 上下文压缩智能体
 *
 * 四层压缩策略（参考Python s08设计）：
 * - L1: snip_compact      — 裁掉中间消息（消息数 > 50）
 * - L2: micro_compact     — 旧tool_result替换为占位符（保留最近3条）
 * - L3: tool_result_budget — 持久化大输出到磁盘（单次消息 > 30KB）
 * - L4: auto_compact      — LLM完整摘要（token超阈值）
 * - Emergency: reactive_compact — API返回prompt_too_long时触发
 *
 * 核心原则：cheap first, expensive last
 * 执行顺序：budget → snip → micro → auto
 *
 * @author hoppinzq
 */
public class Agent06 extends ZQAgent {
    public static ContextCompactor compactor;
    public static TodoManager todoManager;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;
    public static SkillLoader skillLoader;

    /** 供 Tools.compact() 静态方法调用的压缩回调 */
    public static Runnable compactCallback;

    public Agent06(AnthropicClient client, String model, List<ToolDefinition> tools, ContextCompactor compactor, SkillLoader skillLoader, TodoManager todoManager) {
        super(client, model, tools);
        Agent06.compactor = compactor;
        Agent06.skillLoader = skillLoader;
        Agent06.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
        // 注册回调，让 Tools.compact 可以触发实例的 manualCompact
        Agent06.compactCallback = this::manualCompact;
    }

    /**
     * 重写createCommandHandler方法，添加tokens命令和compact命令支持
     */
    @Override
    protected AgentCommandHandler createCommandHandler(SessionManager sessionManager, SkillLoader skillLoader) {
        // 创建一个Runnable来处理/tokens命令，显示当前token估算
        Runnable tokensHandler = () -> {
            showTokenStats();
        };

        // 创建一个Runnable来处理/compact命令，触发手动压缩
        Runnable compactHandler = () -> {
            manualCompact();
        };

        return new AgentCommandHandler(sessionManager, skillLoader, tokensHandler, compactHandler);
    }

    /**
     * 显示当前 token 使用统计
     */
    private void showTokenStats() {
        int currentTokens = compactor.estimateTokens(this.messageParams);
        int currentMessages = this.messageParams.size();
        int threshold = TOKEN_THRESHOLD;

        System.out.println("\u001b[90m========== 当前 Token 统计 ==========\u001b[0m");
        System.out.println("当前消息数: " + currentMessages);
        System.out.println("估算 tokens: " + currentTokens);
        System.out.println("压缩阈值: " + threshold);
        System.out.println("使用率: " + String.format("%.1f%%", (currentTokens * 100.0 / threshold)));

        if (currentTokens >= threshold) {
            System.out.println("\u001b[91m状态: 已超过阈值，建议执行压缩\u001b[0m");
        } else if (currentTokens >= threshold * 0.8) {
            System.out.println("\u001b[93m状态: 接近阈值（80%），建议关注\u001b[0m");
        } else {
            System.out.println("\u001b[92m状态: 正常\u001b[0m");
        }
        System.out.println();
    }

    /**
     * 替换消息列表
     */
    private void replaceMessageParams(List<MessageParam> newParams) {
        this.messageParams.clear();
        this.messageParams.addAll(newParams);
    }

    /**
     * 手动压缩方法：由 compact 工具或命令触发
     *
     * 功能说明：
     * - 直接调用 LLM 生成摘要（参考 Python s08 的 compact_history）
     * - 删除压缩前的上下文
     * - 写入摘要到上下文
     *
     * 执行步骤：
     * 1. 保存完整对话记录到磁盘（.transcripts 目录）
     * 2. 调用 LLM 生成对话摘要（1 API 调用）
     * 3. 用摘要消息替换原始消息列表
     *
     * 注意：三层压缩（budget → snip → micro）是在每次 LLM 调用前自动执行的预处理步骤，
     * 不需要在手动压缩中重复执行。
     */
    private void manualCompact() {
        try {
            if (this.messageParams.isEmpty()) {
                System.out.println("[压缩跳过] 当前上下文为空，无需压缩。");
                return;
            }

            int messageCount = this.messageParams.size();
            int estimatedTokens = ContextCompactor.estimateTokens(this.messageParams);

            System.out.printf("[开始手动压缩] 消息数: %d, 估算tokens: %d%n", messageCount, estimatedTokens);

            // 直接调用 LLM 生成摘要（参考 Python s08 的 compact_history）
            List<MessageParam> compressed = compactor.autoCompact(new ArrayList<>(this.messageParams), "manual");

            // 替换消息列表
            replaceMessageParams(compressed);

            System.out.println("[压缩完成] 上下文已清理，完整历史已保存到会话。下次对话将使用压缩后的上下文。");

        } catch (Exception e) {
            System.err.println("[压缩失败] " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 重写chatMessage方法，实现四层压缩策略
     *
     * 压缩流程（参考Python s08设计）：
     * 1. L3（budget）：持久化大输出到磁盘（0 API调用）
     * 2. L1（snip）：裁掉中间消息（0 API调用）
     * 3. L2（micro）：旧 tool_result 替换为占位符（0 API调用）
     * 4. L4（auto）：当token估算超过阈值时，保存完整对话并生成摘要（1 API调用）
     *
     * 状态同步说明：
     * - ZQAgent将messageParams字段直接传递给此方法（引用传递）
     * - 压缩返回新列表
     * - 发生压缩时，需要同步更新ZQAgent的状态
     *
     * 核心原则：cheap first, expensive last
     *
     * @param messageParams 原始消息参数列表
     * @return LLM响应消息
     */
    @Override
    protected Message chatMessage(List<MessageParam> messageParams) {
        // 执行三层预处理（0 API调用，cheap first）
        // 执行顺序：budget → snip → micro（与Python s08一致）

        // 注意：messageParams 是引用传递，不能直接 clear() 后再使用
        // 需要先保存原始消息，然后逐步处理

        // L3: tool_result_budget — 持久化大输出
        List<MessageParam> working = new ArrayList<>(messageParams);
        working = compactor.toolResultBudget(working);

        // L1: snip_compact — 裁掉中间消息
        working = compactor.snipCompact(working);

        // L2: micro_compact — 旧 tool_result 替换为占位符
        working = compactor.microCompact(working);

        // 将处理后的消息复制回 messageParams
        messageParams.clear();
        messageParams.addAll(working);

        // L4: auto_compact — token仍超阈值时触发（1 API调用，expensive last）
        if (ContextCompactor.estimateTokens(messageParams) > TOKEN_THRESHOLD) {
            System.out.println("[自动 LLM 摘要压缩已触发]");
            List<MessageParam> compactedParams = compactor.autoCompact(new ArrayList<>(messageParams), "auto");

            // 【状态同步】替换 messageParams 并同步 session
            replaceMessageParams(compactedParams);
            return super.chatMessage(compactedParams);
        }

        // 使用（可能被压缩的）消息参数调用父类的chatMessage
        return super.chatMessage(messageParams);
    }

    /**
     * 重写onToolExecution方法，实现压缩策略和待办事项提醒
     *
     * 功能包括：
     * 1. 待办事项提醒：跟踪更新间隔，超过3回合未更新时提醒
     * 2. 执行完整的三层压缩（budget → snip → micro → auto）
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

        // ========== 四层压缩策略 ==========
        // 执行顺序：budget → snip → micro → auto（与Python s08一致）

        // 注意：messageParams 是引用传递，不能直接 clear() 后再使用
        // 需要先保存原始消息，然后逐步处理

        // L3: tool_result_budget — 持久化大输出
        List<MessageParam> working = new ArrayList<>(messageParams);
        working = compactor.toolResultBudget(working);

        // L1: snip_compact — 裁掉中间消息
        working = compactor.snipCompact(working);

        // L2: micro_compact — 旧 tool_result 替换为占位符
        working = compactor.microCompact(working);

        // 将处理后的消息复制回 messageParams
        messageParams.clear();
        messageParams.addAll(working);

        // L4: auto_compact — token仍超阈值时触发
        if (compactor.estimateTokens(messageParams) > TOKEN_THRESHOLD) {
            System.out.println("[自动 LLM 摘要压缩已触发]");
            List<MessageParam> compressed = compactor.autoCompact(new ArrayList<>(messageParams), "auto");
            replaceMessageParams(compressed);
        }
    }

    /**
     * 生成Agent06的系统提示词
     * 
     * @param root 工作目录
     * @param skillDescriptions 技能描述
     * @return 完整的系统提示词
     */
    public static String generateSystemPrompt(String root, String skillDescriptions) {
        return String.format(
                "你是一个具有上下文压缩能力的编程智能体，工作在目录: %s\n\n" +
                "核心能力：\n" +
                "- 使用工具读写文件、编辑代码、执行命令、搜索内容\n" +
                "- 通过 todo 工具管理任务列表，追踪复杂任务进度\n" +
                "- 在对话过程中自动压缩上下文，支持长时间工作\n" +
                "- 加载专业技能以应对特定领域的任务\n\n" +
                "工作指南：\n" +
                "1. 优先使用工具解决任务，而非仅提供建议\n" +
                "2. 在处理复杂或不熟悉的主题时，使用 load_skill 工具获取专业知识\n" +
                "3. 使用 todo 工具将大任务分解为小步骤，逐个完成\n" +
                "4. 当上下文过大时，系统会自动压缩历史对话，保持连贯性\n\n" +
                "可用的技能：\n%s",
                root,
                skillDescriptions
        );
    }

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(API_KEY)
            .baseUrl(BASE_URL)
            .timeout(Duration.ofSeconds(TIMEOUT))
            .maxRetries(MAX_RETRIES)
            .build();

        // 创建技能加载器
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        // 先创建 SessionManager
        SessionManager sessionManager = bootstrapSession(args);

        // 创建 ContextCompactor，传入 SessionManager
        compactor = new ContextCompactor(client, MODEL, sessionManager);

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

        tools.add(ContentCompactDefinition);
        Agent06 agent = new Agent06(client, MODEL, tools, compactor, skillLoader, todoManager);

        // 使用新方法构建系统提示词
        String systemPrompt = generateSystemPrompt(ROOT, skillLoader.getDescriptions());
        agent.setSystemPrompt(systemPrompt);

        System.out.println("=== 上下文压缩智能体 ===");
        System.out.println("四层压缩策略（参考Python s08设计）：");
        System.out.println("  L1 snip_compact: 最大消息数 = " + MAX_MESSAGES + ", 保留头部 = " + KEEP_HEAD);
        System.out.println("  L2 micro_compact: 保留最近工具结果数 = " + KEEP_RECENT);
        System.out.println("  L3 budget: 持久化阈值 = " + (PERSIST_THRESHOLD / 1024) + "KB, 单次消息最大 = " + (MAX_BYTES_PER_MESSAGE / 1024) + "KB");
        System.out.println("  L4 auto_compact: token阈值 = " + CONTEXT_LIMIT);
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
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
}
