package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.mcp.MCPAgent;
import com.hoppinzq.agent.tool.mcp.McpConfigLoader;
import com.hoppinzq.agent.tool.mcp.McpLoader;
import com.hoppinzq.agent.tool.mcp.McpSetting;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * AgentRed95 - 红警95游戏智能体（MCP工具集成）
 *
 * 集成Model Context Protocol (MCP)服务器，扩展AI能力边界
 *
 * 核心特性：
 * - 支持多种MCP传输方式（STDIO、SSE、Streamable HTTP）
 * - 自动加载和注册MCP服务器提供的工具
 * - 统一的工具调用接口和错误处理
 * - 支持同步和异步MCP客户端
 * - 自动游戏循环引擎（runGameLoop）
 * - 四层上下文压缩 + 待办事项管理 + 策略技能加载
 *
 * 使用MCP服务器的场景：
 * - 需要访问外部数据源或API
 * - 需要执行特定领域的高级操作
 * - 需要与第三方服务集成
 *
 * @author hoppinzq
 */
public class AgentRed95 extends ZQAgent {
    public static ContextCompactor compactor;
    public static TodoManager todoManager;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;
    public static SkillLoader skillLoader;

    /** 供 Tools.compact() 静态方法调用的压缩回调 */
    public static Runnable compactCallback;

    public AgentRed95(LlmProvider provider, String model, List<ToolDefinition> tools, ContextCompactor compactor, SkillLoader skillLoader, TodoManager todoManager) {
        super(provider, model, tools);
        AgentRed95.compactor = compactor;
        AgentRed95.skillLoader = skillLoader;
        AgentRed95.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
        // 注册回调，让 Tools.compact 可以触发实例的 manualCompact
        AgentRed95.compactCallback = this::manualCompact;
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
        int currentTokens = compactor.countTokens(this.messageParams);
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
    private void replaceMessageParams(List<LlmMessage> newParams) {
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
            int estimatedTokens = compactor.countTokens(this.messageParams);

            System.out.printf("[开始手动压缩] 消息数: %d, 估算tokens: %d%n", messageCount, estimatedTokens);

            List<LlmMessage> compressed = compactor.autoCompact(new ArrayList<>(this.messageParams), "manual");

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
    protected LlmResponse chatMessage(List<LlmMessage> messageParams) {
        // 执行三层预处理（0 API调用，cheap first）
        // 执行顺序：budget → snip → micro（与Python s08一致）

        // 注意：messageParams 是引用传递，不能直接 clear() 后再使用
        // 需要先保存原始消息，然后逐步处理

        // L3: tool_result_budget — 持久化大输出
        List<LlmMessage> working = new ArrayList<>(messageParams);
        working = compactor.toolResultBudget(working);

        // L1: snip_compact — 裁掉中间消息
        working = compactor.snipCompact(working);

        // L2: micro_compact — 旧 tool_result 替换为占位符
        working = compactor.microCompact(working);

        // 将处理后的消息复制回 messageParams
        messageParams.clear();
        messageParams.addAll(working);

        // L4: auto_compact — token仍超阈值时触发（1 API调用，expensive last）
        if (compactor.countTokens(messageParams) > TOKEN_THRESHOLD) {
            System.out.println("[自动 LLM 摘要压缩已触发]");
            List<LlmMessage> compactedParams = compactor.autoCompact(new ArrayList<>(messageParams), "auto");

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
    protected void onToolExecution(List<LlmMessage> toolResults) {
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
            toolResults.add(LlmMessage.user(reminder));
        }

        // ========== 四层压缩策略 ==========
        // 执行顺序：budget → snip → micro → auto（与Python s08一致）

        // 注意：messageParams 是引用传递，不能直接 clear() 后再使用
        // 需要先保存原始消息，然后逐步处理

        // L3: tool_result_budget — 持久化大输出
        List<LlmMessage> working = new ArrayList<>(messageParams);
        working = compactor.toolResultBudget(working);

        // L1: snip_compact — 裁掉中间消息
        working = compactor.snipCompact(working);

        // L2: micro_compact — 旧 tool_result 替换为占位符
        working = compactor.microCompact(working);

        // 将处理后的消息复制回 messageParams
        messageParams.clear();
        messageParams.addAll(working);

        // L4: auto_compact — token仍超阈值时触发
        if (compactor.countTokens(messageParams) > TOKEN_THRESHOLD) {
            System.out.println("[自动 LLM 摘要压缩已触发]");
            List<LlmMessage> compressed = compactor.autoCompact(new ArrayList<>(messageParams), "auto");
            replaceMessageParams(compressed);
        }
    }

    public static void main(String[] args) {

        // 检查是否为自动游戏模式
        boolean autoMode = false;
        String[] sessionArgs = args;
        if (args.length > 0 && "auto".equalsIgnoreCase(args[0])) {
            autoMode = true;
            sessionArgs = args.length > 1
                    ? java.util.Arrays.copyOfRange(args, 1, args.length)
                    : new String[0];
        }

        // 协议中立 Provider：由 AIConstants.PROVIDER 切换 openai / anthropic
        LlmProvider provider = LlmProviders.create();

        // 配置MCP服务器
        List<McpSetting> settings = new McpConfigLoader().loadMcpSettings();

        // 初始化MCP客户端
        MCPAgent mcpAgent = new MCPAgent(settings);
        mcpAgent.initializeClient();

        McpLoader mcpLoader = new McpLoader(settings);

        // 创建技能加载器
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        // 先创建 SessionManager
        SessionManager sessionManager = bootstrapSession(sessionArgs);

        // 创建 ContextCompactor，传入 SessionManager
        compactor = new ContextCompactor(provider, MODEL, sessionManager);
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(ReadFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(EditFileDefinition);
        tools.add(GlobDefinition);
        tools.add(ContentSearchDefinition);
        tools.add(SkillsDefinition);
        tools.add(TodoDefinition);
        tools.add(ContentCompactDefinition);

        tools.addAll(mcpLoader.loadTools());

        // 修复：使用 AgentRed95 而非 ZQAgent，确保压缩和待办功能生效
        AgentRed95 agent = new AgentRed95(provider, MODEL, tools, compactor, skillLoader, todoManager);

        // 构建系统提示
        String systemPrompt = buildSystemPrompt(mcpLoader);
        agent.setSystemPrompt(systemPrompt);

        try {
            agent.setSessionManager(sessionManager, skillLoader);
            // 设置 MCP 命令支持
            if (agent.getCommandHandler() != null) {
                agent.getCommandHandler().setMcpAgent(mcpAgent);
                agent.getCommandHandler().setMcpLoader(mcpLoader);
            }
            if (autoMode) {
                System.out.println("\u001b[95m=== 自动游戏模式已启动 ===\u001b[0m");
                String initialPrompt = "开始红警95游戏！请按照你的战略计划执行：" +
                        "开局部署基地、发展经济、组建军队、摧毁敌方取得胜利。" +
                        "不要停下，直到赢得游戏。";
                agent.runGameLoop(initialPrompt);
            } else {
                agent.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mcpAgent.closeClient();
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
     * 构建系统提示
     *
     * @param mcpLoader MCP加载器
     * @return 系统提示字符串
     */
    private static String buildSystemPrompt(McpLoader mcpLoader) {
        String skillList = skillLoader != null ? skillLoader.getDescriptions() : "（技能未加载）";
        return String.format("""
                # 红警95 实时战略指挥官

                你是红警95游戏指挥官AI，由hoppinzq创建。你的唯一使命：**摧毁所有敌方势力，取得胜利**。
                你只需要执行游戏动作，不需要编写代码或处理编程任务。

                ## 敌人在哪
                敌人在地图右上方！！！

                ## 铁律（违反将导致任务失败）

                1. **每次行动前先 get_game_state**：获取最新资源、电力、可见单位及其真实 actor_id。
                2. **只传真实整数 actor_id**：工具参数中的 actorId/actorIds 必须是工具返回的整数ID，**禁止**传入 "MCV"、"actor_1" 等字符串。如果不知道ID，先调 get_game_state。
                3. **结果驱动**：每次调用工具后，根据返回结果判断是否成功，再决定下一步。不要盲目重复调用。
                4. **绝不连续重复调用同一工具**：如果同一工具连续调用3次结果不变，说明方法不对，换策略。
                5. **不赢不停止**：游戏未胜利就持续行动，每回合至少调用一个工具，绝不空转。

                ## 每回合决策流程（OODA循环）

                1. **观察**：调用 `get_game_state` → 查看金钱、电力、所有可见单位（含 actor_id）
                2. **判断**：当前最紧迫的威胁或机会是什么？（没电？没钱？被攻击？发现敌人？）
                3. **决策**：选择最高优先级行动
                4. **执行**：调用工具 → 看结果 → 调整策略 → 进入下一轮

                ## 策略优先级

                ```
                经济基础(P0) > 紧急防御(P0) > 侦查情报(P1) > 基础军队(P1) > 科技升级(P2) > 全面进攻(P3)
                ```

                ### 开局标准流程（前5个动作）
                1. `deploy_mcv` — 展开基地车（一局只调一次）
                2. `try_buy_building_and_build("POWER")` → `place_building("BUILDING")` — 造电厂
                3. `try_buy_building_and_build("PROC")` → `place_building("BUILDING")` — 造矿厂
                4. `try_buy_building_and_build("BARRACKS")` → `place_building("BUILDING")` — 造兵营
                5. `produce("E1", 5)` — 生产步枪兵

                > 建筑必须走两步：先 try_buy_building_and_build（等就绪），再 place_building（落成）。跳过第2步建筑不出现。

                ## 可用策略技能

                使用 `load_skill(skill_name="...")` 加载详细策略指导：
                %s

                建议开局加载 game-loop 和 game-strategy 技能获取详细决策清单。

                ## 工具使用要点

                - `start_game`：仅当用户要求开启游戏时调用。调用后不要执行任何其他工具！
                - `get_game_state`：优先用这个，而不是分别调 query_player_info + visible_units
                - `move_units_and_wait`：会长时间阻塞，仅在必须确认到位时用
                - `query_map_info`：返回数据很大，只在战略决策时偶尔调用
                - `is_game_run`：仅在刚开始或怀疑断线时调一次

                ## MCP 游戏工具完整列表

                %s

                ---
                你是战场指挥官。观察、决策、执行、胜利。开始行动吧！
                """, skillList, mcpLoader.getToolDescriptions());
    }


}
