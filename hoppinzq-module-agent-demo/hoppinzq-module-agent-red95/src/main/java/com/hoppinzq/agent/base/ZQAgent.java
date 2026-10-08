package com.hoppinzq.agent.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.session.SubAgentSessionResult;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;
import lombok.Data;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 智能体基类
 * <p>只面向协议中立的 {@link LlmProvider}/{@link LlmMessage} 编程，
 * 协议差异（Anthropic / OpenAI）由 Provider 实现类封装。
 *
 * @author hoppinzq
 */
@Data
public class ZQAgent {
    private String systemPrompt;
    private final Scanner scanner;
    private final String model;
    protected final LlmProvider provider;
    private final List<ToolDefinition> tools;
    protected List<LlmMessage> messageParams = new ArrayList<>();
    private String taskResult;
    private boolean taskCompleted = false;
    /**
     * 可选的命令处理器；设置后可处理 /stats、/usage、/skills、/exit 等特殊命令。
     */
    private AgentCommandHandler commandHandler;

    /**
     * 会话管理器
     */
    protected SessionManager sessionManager;

    public ZQAgent(LlmProvider provider, String model, List<ToolDefinition> tools) {
        this.provider = provider;
        this.model = model;
        this.scanner = new Scanner(System.in);
        this.tools = tools;
    }

    /**
     * 设置会话管理器，同时初始化命令处理器。
     * 子类可以重写此方法以提供额外的命令处理器（如 compact 命令）。
     */
    public void setSessionManager(SessionManager sessionManager, SkillLoader skillLoader) {
        this.sessionManager = sessionManager;
        this.commandHandler = sessionManager != null ? createCommandHandler(sessionManager, skillLoader) : null;
    }

    /**
     * 创建命令处理器。子类可以重写此方法以提供自定义的命令处理器。
     *
     * @param sessionManager 会话管理器
     * @param skillLoader 技能加载器
     * @return 命令处理器，默认不包含 compact 命令支持
     */
    protected AgentCommandHandler createCommandHandler(SessionManager sessionManager, SkillLoader skillLoader) {
        return new AgentCommandHandler(sessionManager, skillLoader, null);
    }

    public void run() {
        if (sessionManager != null) {
            int n = sessionManager.historySize();
            if (n > 0) {
                sessionManager.populate(messageParams);
                System.out.printf("\u001b[90m已恢复会话 %s，共 %d 条历史消息\u001b[0m%n",
                        sessionManager.getSessionId(), n);
            } else {
                System.out.printf("\u001b[90m新会话 %s\u001b[0m%n", sessionManager.getSessionId());
            }
        }
        System.out.println("开始对话吧（输入 /stats 查看统计，/usage 查看明细，/mcp 查看mcp列表，/skills 查看技能，/tokens 查看上下文，/transcripts 查看压缩历史，/compact 手动压缩，/exit 退出）");
        while (true) {
            System.out.print("\u001b[94m你\u001b[0m: ");
            String userInput = scanner.nextLine();
            if (userInput.isEmpty()) {
                continue;
            }
            // 特殊命令：不发送给 LLM
            if (commandHandler != null && commandHandler.isCommand(userInput)) {
                commandHandler.handleCommand(userInput);
                continue;
            }
            appendMessage(LlmMessage.user(userInput));

            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                continue;
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                System.out.println("错误: 响应中没有消息");
                continue;
            }
            appendMessage(message);
            recordUsageIfNeeded(response);
            // 每次 create 后都打印 assistant 的文本输出，避免纯文本回复（无工具调用）被静默吞掉
            printText(message);

            // canonical agentic loop：以 finish_reason == tool_calls 为循环条件
            while (hasPendingToolCalls(response)) {
                List<LlmMessage> toolResults = executeToolCalls(message);
                if (toolResults.isEmpty()) {
                    // 防御：本轮没有任何有效工具调用，继续请求只会死循环
                    break;
                }
                messageParams.addAll(toolResults);
                if (sessionManager != null) {
                    sessionManager.onToolMessagesAppended(toolResults);
                }

                try {
                    response = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                message = response.getMessage();
                if (message == null) {
                    System.out.println("错误: 响应中没有消息");
                    break;
                }
                appendMessage(message);
                recordUsageIfNeeded(response);
                printText(message);
            }
            // 非 tool_calls 退出：检查是否被截断
            warnIfTruncated(response);
        }
    }

    /**
     * 自动游戏循环引擎（Loop Engine）
     *
     * <p>与交互式 {@link #run()} 不同，此方法不等待用户输入。
     * 当AI停止工具调用时，自动注入续推指令驱动AI继续行动，
     * 直到检测到胜利/失败或达到最大回合数。
     *
     * <p>核心机制：
     * <ul>
     *   <li>每轮执行完整的 agentic tool loop</li>
     *   <li>AI停止时注入 {@code <game_continuation>} 续推指令</li>
     *   <li>连续无进展时逐步增强提示强度</li>
     *   <li>胜利关键词检测终止循环</li>
     *   <li>最大回合安全阀防止无限循环</li>
     * </ul>
     *
     * @param initialPrompt 初始游戏指令
     */
    public void runGameLoop(String initialPrompt) {
        // 恢复会话历史
        if (sessionManager != null) {
            int n = sessionManager.historySize();
            if (n > 0) {
                sessionManager.populate(messageParams);
                System.out.printf("\u001b[90m已恢复会话 %s，共 %d 条历史消息\u001b[0m%n",
                        sessionManager.getSessionId(), n);
            } else {
                System.out.printf("\u001b[90m新会话 %s\u001b[0m%n", sessionManager.getSessionId());
            }
        }

        final int MAX_TURNS = 500;
        int turnCount = 0;
        int noProgressCount = 0;
        String currentPrompt = initialPrompt;

        while (true) {
            // ========== 发送当前轮提示 ==========
            appendMessage(LlmMessage.user(currentPrompt));
            turnCount++;
            System.out.printf("%n\u001b[95m======== 回合 %d ========\u001b[0m%n", turnCount);

            // ========== 执行 LLM 调用 ==========
            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                System.out.println("错误: " + e.getMessage());
                e.printStackTrace();
                break;
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                System.out.println("错误: 响应中没有消息");
                break;
            }
            appendMessage(message);
            recordUsageIfNeeded(response);
            printText(message);

            // ========== Agentic Tool Loop ==========
            while (hasPendingToolCalls(response)) {
                List<LlmMessage> toolResults;
                try {
                    toolResults = executeToolCalls(message);
                } catch (Exception e) {
                    System.out.println("工具执行错误: " + e.getMessage());
                    break;
                }
                if (toolResults.isEmpty()) {
                    // 防御：本轮没有任何有效工具调用，继续请求只会死循环
                    break;
                }
                messageParams.addAll(toolResults);
                if (sessionManager != null) {
                    sessionManager.onToolMessagesAppended(toolResults);
                }

                try {
                    response = chatMessage(messageParams);
                } catch (Exception e) {
                    System.out.println("错误: " + e.getMessage());
                    break;
                }
                message = response.getMessage();
                if (message == null) {
                    System.out.println("错误: 响应中没有消息");
                    break;
                }
                appendMessage(message);
                recordUsageIfNeeded(response);
                printText(message);
            }
            warnIfTruncated(response);

            // ========== 胜利检测 ==========
            String lastText = extractText(message);
            if (isVictoryDeclared(lastText)) {
                System.out.println("\n\u001b[92m========== 游戏胜利！==========\u001b[0m");
                System.out.println("AI 已在 " + turnCount + " 个回合内取得胜利。");
                break;
            }

            // ========== 防卡死机制 ==========
            // 判断本轮是否有有效进展（有文本输出且有工具调用）
            boolean hadToolUse = message.getToolCalls() != null && !message.getToolCalls().isEmpty();
            if (!hadToolUse && lastText.isBlank()) {
                noProgressCount++;
            } else {
                noProgressCount = 0;
            }

            // ========== 生成续推指令 ==========
            currentPrompt = buildContinuationPrompt(turnCount, noProgressCount);

            // ========== 安全阀 ==========
            if (turnCount >= MAX_TURNS) {
                System.out.println("\n\u001b[93m达到最大回合数 " + MAX_TURNS + "，终止游戏循环。\u001b[0m");
                break;
            }

            // 短暂暂停避免CPU过载
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 从 assistant 消息中提取文本内容（无文本返回空串）
     */
    private String extractText(LlmMessage message) {
        return message.getText() == null ? "" : message.getText();
    }

    /**
     * 检测AI是否宣布胜利
     */
    private boolean isVictoryDeclared(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        return lower.contains("胜利") || lower.contains("victory")
                || lower.contains("游戏结束") || lower.contains("game over")
                || lower.contains("we won") || lower.contains("敌军覆灭");
    }

    /**
     * 构建续推指令，根据回合数和无进展次数动态调整强度
     */
    private String buildContinuationPrompt(int turnCount, int noProgressCount) {
        // 高强度提醒：连续5轮无进展
        if (noProgressCount >= 5) {
            return "<force_action>\n" +
                    "你已经连续多轮没有有效进展！请立即执行以下操作：\n" +
                    "1. 调用 is_game_run 确认游戏是否仍在运行\n" +
                    "2. 如果游戏已结束，请声明胜利\n" +
                    "3. 如果游戏仍在运行，调用 get_game_state 刷新状态后立即行动\n" +
                    "4. 如果之前的策略无效，换一种方式（造新建筑/侦查新区域/从不同方向进攻）\n" +
                    "</force_action>";
        }

        // 中等提醒：连续3轮无进展
        if (noProgressCount >= 3) {
            return "<stuck_warning>\n" +
                    "回合 " + turnCount + "。你似乎卡住了。请：\n" +
                    "1. 调用 get_game_state 重新评估局势\n" +
                    "2. 如果资金不足，优先恢复经济（造矿厂/矿车）\n" +
                    "3. 如果建筑受阻，检查电力是否足够\n" +
                    "4. 如果军事受阻，换方向侦查或进攻\n" +
                    "不要停下，继续行动！\n" +
                    "</stuck_warning>";
        }

        // 常规续推：每10回合注入一次策略提醒
        if (turnCount % 10 == 0) {
            return "<game_continuation>\n" +
                    "回合 " + turnCount + "。游戏尚未结束，请继续执行你的战略计划：\n" +
                    "1. 调用 get_game_state 刷新状态，查看资源和单位\n" +
                    "2. 评估当前局势：经济是否健康？军队是否足够？敌人位置在哪？\n" +
                    "3. 选择最高优先级行动并执行\n" +
                    "4. 不要停下，直到取得胜利！\n" +
                    "战略提醒：经济基础 > 侦查情报 > 科技升级 > 军事扩张\n" +
                    "</game_continuation>";
        }

        // 标准续推
        return "<game_continuation>\n" +
                "回合 " + turnCount + "。游戏尚未结束，请继续：\n" +
                "1) 调用 get_game_state 刷新状态\n" +
                "2) 根据局势决定下一步行动\n" +
                "3) 不要停下，直到取得胜利\n" +
                "</game_continuation>";
    }

    /**
     * 打印 assistant 消息中的文本。空文本跳过。
     * <p>DeepSeek 等后端返回 tool_calls 时文本常为 null。
     */
    private void printText(LlmMessage message) {
        String text = message.getText();
        if (text != null && !text.isBlank()) {
            System.out.printf("\u001b[93mAI\u001b[0m: %s%n", text);
        }
    }

    /**
     * 判断是否需要继续工具循环。
     * <p>以 {@code finish_reason == tool_calls} 为主判据；个别 OpenAI 兼容后端
     * 返回 tool_calls 时 finish_reason 可能仍是 stop，故兜底检查 toolCalls 非空。
     */
    private boolean hasPendingToolCalls(LlmResponse response) {
        if (response.getFinishReason() == LlmResponse.FinishReason.TOOL_CALLS) {
            return true;
        }
        LlmMessage message = response.getMessage();
        return message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    /** 命中 LENGTH 时打印警告，避免静默截断工具调用导致死循环。 */
    private void warnIfTruncated(LlmResponse response) {
        if (response.getFinishReason() == LlmResponse.FinishReason.LENGTH) {
            System.out.printf("\u001b[91m[警告]\u001b[0m 本轮回复被 max_tokens=%d 截断，工具调用可能不完整。建议调大 MAX_TOKENS。%n",
                    MAX_TOKENS);
        }
    }

    /**
     * 执行一轮 assistant 回复中的所有工具调用，每个调用对应一条 {@code role=tool} 消息。
     * <p>文本已由 {@link #printText(LlmMessage)} 处理，这里只负责工具；
     * 找不到工具或执行抛异常都以普通文本回灌（协议没有 isError 标记）。
     * <p>协议要求每个 tool_call_id 必须有且仅有一条应答消息（含失败），否则下次请求 400。
     */
    private List<LlmMessage> executeToolCalls(LlmMessage message) {
        List<LlmMessage> toolResults = new ArrayList<>();
        for (LlmMessage.ToolCall call : message.getToolCalls()) {
            String callId = call.getId();
            String toolName = call.getName();
            String arguments = call.getArgumentsJson();
            System.out.printf("\u001b[96m工具\u001b[0m: %s(%s)%n", toolName, arguments);

            String toolResult = null;
            Exception toolError = null;
            ToolDefinition matched = null;
            for (ToolDefinition tool : tools) {
                if (tool.getName().equals(toolName)) {
                    matched = tool;
                    break;
                }
            }
            if (matched == null) {
                toolError = new Exception("工具 '" + toolName + "' 没有找到");
                System.out.printf("\u001b[91m错误\u001b[0m: %s%n", toolError.getMessage());
            } else {
                try {
                    toolResult = invokeTool(matched, arguments);
                    System.out.printf("\u001b[92m结果\u001b[0m: %s%n", toolResult);
                } catch (Exception e) {
                    toolError = e;
                    System.out.printf("\u001b[91m错误\u001b[0m: %s%n", e.getMessage());
                    e.printStackTrace();
                }
            }

            toolResults.add(LlmMessage.tool(callId,
                    toolError != null ? "错误: " + toolError.getMessage() : toolResult));
        }
        return toolResults;
    }

    /**
     * 记录本次 LLM 调用的 token 使用情况（若设置了 SessionManager）。
     */
    private void recordUsageIfNeeded(LlmResponse response) {
        if (sessionManager != null && response.getUsage() != null) {
            sessionManager.recordUsage(response.getUsage());
        }
    }

    /**
     * 向 messageParams 追加一条消息；若设置了 {@link SessionManager}，
     * 同步持久化。所有需要记录历史的追加都应走此方法。
     * <p>注意：一轮的多条 {@code role=tool} 结果不走此方法，
     * 由 {@link SessionManager#onToolMessagesAppended(List)} 合并落盘。
     */
    protected void appendMessage(LlmMessage message) {
        messageParams.add(message);
        if (sessionManager != null) {
            sessionManager.onMessageAppended(message);
        }
    }

    /**
     * 工具执行后的回调钩子，子类可重写以注入额外内容（如提醒、压缩等）。
     * 注意：基类的 {@link #executeToolCalls(LlmMessage)} 不再调用此方法；
     * 仅为兼容已有子类重写而保留。
     */
    protected void onToolExecution(List<LlmMessage> toolResults) {
        // 默认空实现
    }

    private String invokeTool(ToolDefinition tool, String arguments) throws Exception {
        JsonNode input = OBJECT_MAPPER.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        if (tool.getType() == null) {
            if (!input.isObject()) {
                throw new IllegalArgumentException("工具 '" + tool.getName() + "' 参数不是 JSON 对象");
            }
            // 保持旧的包装格式：{"input": {...}, "tool_name": "..."}
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.set("input", input);
            root.put("tool_name", tool.getName());
            return tool.getFunction().apply(root.toString());
        } else {
            // 工具自定义了入参 POJO 类型：先规整化再序列化，与旧实现行为一致
            Object pojo = OBJECT_MAPPER.treeToValue(input, tool.getType());
            return tool.getFunction().apply(OBJECT_MAPPER.writeValueAsString(pojo));
        }
    }

    protected LlmResponse chatMessage(List<LlmMessage> messageParams) {
        return provider.complete(LlmRequest.builder()
                .model(model)
                .systemPrompt(systemPrompt)
                .messages(messageParams)
                .tools(tools)
                .maxTokens(MAX_TOKENS)
                .build());
    }

    public String runTask(String prompt) {
        messageParams.add(LlmMessage.user(prompt));

        while (true) {
            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                return "Error: 响应中没有消息";
            }
            messageParams.add(message);

            List<LlmMessage> toolResults = new ArrayList<>();
            boolean hasToolUse = false;

            if (message.getText() != null && !message.getText().isBlank()) {
                System.out.printf("\u001b[94m（子）AI\u001b[0m: %s%n", message.getText());
            }
            for (LlmMessage.ToolCall call : message.getToolCalls()) {
                hasToolUse = true;
                System.out.printf("\u001b[96m（子）工具\u001b[0m: %s(%s)%n", call.getName(), call.getArgumentsJson());

                if ("task_completed".equals(call.getName())) {
                    try {
                        JsonNode input = OBJECT_MAPPER.readTree(
                                call.getArgumentsJson() == null || call.getArgumentsJson().isBlank()
                                        ? "{}" : call.getArgumentsJson());
                        JsonNode res = input.get("result");
                        if (res != null && res.isTextual()) {
                            return res.asText();
                        }
                        return "Task completed.";
                    } catch (Exception e) {
                        return "Error parsing task_completed: " + e.getMessage();
                    }
                }

                String toolResult = null;
                Exception toolError = null;
                boolean toolFound = false;

                for (ToolDefinition tool : tools) {
                    if (tool.getName().equals(call.getName())) {
                        try {
                            toolResult = invokeTool(tool, call.getArgumentsJson());
                            System.out.printf("\u001b[92m（子）结果\u001b[0m: %s%n", toolResult);
                        } catch (Exception e) {
                            toolError = e;
                            System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", e.getMessage());
                            e.printStackTrace();
                        }
                        toolFound = true;
                        break;
                    }
                }

                if (!toolFound) {
                    toolError = new Exception("工具 '" + call.getName() + "' 没有找到");
                    System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", toolError.getMessage());
                }

                toolResults.add(LlmMessage.tool(call.getId(),
                        toolError != null ? "错误: " + toolError.getMessage() : toolResult));
            }

            if (!hasToolUse) {
                // 没有工具调用：文本回复即任务结果
                warnIfTruncated(response);
                return message.getText() == null ? "" : message.getText();
            }
            messageParams.addAll(toolResults);
        }
    }

    /**
     * 执行任务并返回包含 token 使用情况的结果
     *
     * @param prompt 任务提示词
     * @return 包含结果和 token 使用情况的 SubAgentSessionResult
     */
    public SubAgentSessionResult runTaskWithTokenUsage(String prompt) {
        long totalInputTokens = 0;
        long totalOutputTokens = 0;

        messageParams.add(LlmMessage.user(prompt));

        while (true) {
            LlmResponse response;
            try {
                response = chatMessage(messageParams);
            } catch (Exception e) {
                return SubAgentSessionResult.error("Error: " + e.getMessage());
            }
            LlmMessage message = response.getMessage();
            if (message == null) {
                return SubAgentSessionResult.error("Error: 响应中没有消息");
            }
            messageParams.add(message);

            // 累积 token 使用情况
            if (response.getUsage() != null) {
                totalInputTokens += response.getUsage().getInputTokens() != null ? response.getUsage().getInputTokens() : 0;
                totalOutputTokens += response.getUsage().getOutputTokens() != null ? response.getUsage().getOutputTokens() : 0;
            }

            List<LlmMessage> toolResults = new ArrayList<>();
            boolean hasToolUse = false;

            if (message.getText() != null && !message.getText().isBlank()) {
                System.out.printf("\u001b[94m（子）AI\u001b[0m: %s%n", message.getText());
            }
            for (LlmMessage.ToolCall call : message.getToolCalls()) {
                hasToolUse = true;
                System.out.printf("\u001b[96m（子）工具\u001b[0m: %s(%s)%n", call.getName(), call.getArgumentsJson());

                if ("task_completed".equals(call.getName())) {
                    try {
                        JsonNode input = OBJECT_MAPPER.readTree(
                                call.getArgumentsJson() == null || call.getArgumentsJson().isBlank()
                                        ? "{}" : call.getArgumentsJson());
                        JsonNode res = input.get("result");
                        if (res != null && res.isTextual()) {
                            return SubAgentSessionResult.success(res.asText(), totalInputTokens, totalOutputTokens);
                        }
                        return SubAgentSessionResult.success("Task completed.", totalInputTokens, totalOutputTokens);
                    } catch (Exception e) {
                        return SubAgentSessionResult.error("Error parsing task_completed: " + e.getMessage());
                    }
                }

                String toolResult = null;
                Exception toolError = null;
                boolean toolFound = false;

                for (ToolDefinition tool : tools) {
                    if (tool.getName().equals(call.getName())) {
                        try {
                            toolResult = invokeTool(tool, call.getArgumentsJson());
                            System.out.printf("\u001b[92m（子）结果\u001b[0m: %s%n", toolResult);
                        } catch (Exception e) {
                            toolError = e;
                            System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", e.getMessage());
                            e.printStackTrace();
                        }
                        toolFound = true;
                        break;
                    }
                }

                if (!toolFound) {
                    toolError = new Exception("工具 '" + call.getName() + "' 没有找到");
                    System.out.printf("\u001b[91m（子）错误\u001b[0m: %s%n", toolError.getMessage());
                }

                toolResults.add(LlmMessage.tool(call.getId(),
                        toolError != null ? "错误: " + toolError.getMessage() : toolResult));
            }

            if (!hasToolUse) {
                warnIfTruncated(response);
                return SubAgentSessionResult.success(message.getText() == null ? "" : message.getText(),
                        totalInputTokens, totalOutputTokens);
            }

            messageParams.addAll(toolResults);
        }
    }
}
