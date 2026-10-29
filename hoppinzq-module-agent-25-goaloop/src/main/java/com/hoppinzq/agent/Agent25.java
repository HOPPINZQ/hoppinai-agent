package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.goal.GoalCapability;
import com.hoppinzq.agent.capability.goal.GoalConfig;
import com.hoppinzq.agent.capability.goal.GoalDecision;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * Goal Loop 智能体（对应 Python 教程 s17_goal_loop）。
 *
 * <p>本类只负责<b>装配</b>：目标状态、判断器、/goal 命令、提示词片段全部收拢在
 * {@link GoalCapability}（capability.goal 包）。目标循环对主循环的改写按规约保留为
 * 子类覆写：{@link #chatMessage(List)} 是内核循环的唯一请求出口，在其返回前做
 * <b>Goal Gate</b> —— 模型不再调用工具只代表这一轮想停，目标是否完成交给独立判断器：
 * 未满足就把理由回灌到同一份 messages 再次请求，没有单独的 continuation queue。
 *
 * <p>自动继续的出口：{@code GoalConfig.GOAL_STOP_HOOK_BLOCK_CAP}（判断器连续阻止上限）
 * 与 {@code GoalConfig.MAX_TURNS}（单次 Gate 链的续轮上限）。
 * 达到上限把控制权还给用户，但目标不会伪装成完成。
 *
 * <p>示例：
 * <pre>
 *   /goal python -m pytest 退出码为 0
 *   MAX_TURNS=20 java Agent25
 * </pre>
 *
 * @author hoppinzq
 */
public class Agent25 extends ZQAgent {

    /** Goal 能力：Gate 与 /goal 前缀分发都要用到，保存为字段 */
    private final GoalCapability goal;

    public Agent25(LlmProvider provider, String model, List<ToolDefinition> tools, GoalCapability goal) {
        super(provider, model, tools);
        this.goal = goal;
    }

    /**
     * 基础系统提示词（不含能力片段；能力通过 promptFragments 贡献自己的部分）。
     * 关键要求：运行验证命令后，把命令和结果明确写进对话，
     * 让独立判断器能够检查 —— 判断器只能读对话，对话里没有的证据等于不存在。
     */
    public static String generateSystemPrompt(String root) {
        String osName = System.getProperty("os.name").toLowerCase();
        return String.format("""
                ## 角色
                你是一个编程智能体，用工具检查和修改当前仓库，直到用户目标真正完成。

                ## 工作要求（重要）
                - 运行验证命令后，把命令和退出码/结果明确写进对话，让独立判断器能够检查。
                - 不要宣称没有结果支撑的完成。对话里没有的证据等于不存在。

                ## 可用工具
                - bash / read_file / write_file / edit_file / list_files / content_search

                ## 环境
                - 操作系统：%s
                - 工作目录：%s""", osName, root);
    }

    /**
     * Goal Gate（主循环改写的保留点）：内核每次请求模型都经过本方法；
     * 当模型这轮不再调用工具且存在活跃目标时，先让判断器裁决：
     * <ul>
     *   <li>{@code block}    —— 把理由回灌对话，立即再次请求（自动续轮）</li>
     *   <li>{@code achieved} / {@code failed} / {@code limit} / {@code error} —— 打印裁决后正常结束</li>
     *   <li>{@code allow}    —— 无活跃目标，退出条件与基础循环一致</li>
     * </ul>
     */
    @Override
    protected LlmResponse chatMessage(List<LlmMessage> messageParams) {
        LlmResponse response = super.chatMessage(messageParams);
        if (!goal.hasActiveGoal()) {
            return response;
        }
        int continuations = 0;
        while (goal.hasActiveGoal() && !hasPendingToolCalls(response)
                && (GoalConfig.MAX_TURNS <= 0 || continuations < GoalConfig.MAX_TURNS)) {
            GoalDecision decision = goal.evaluateAfterTurn(messageParams);
            if (!decision.isBlock()) {
                if (!GoalDecision.ALLOW.equals(decision.action())) {
                    System.out.printf("[goal] %s: %s%n", decision.action(), decision.reason());
                }
                break;  // 真正结束：allow / achieved / failed / limit / error
            }
            // 未完成：把理由回灌到同一份对话，立即进入下一轮（无 continuation queue）
            continuations++;
            appendMessage(LlmMessage.user(goal.continueMessage(decision)));
            response = super.chatMessage(messageParams);
        }
        return response;
    }

    /**
     * 判断是否需要继续工具循环（与内核主循环同一判据，Gate 复用）。
     */
    private boolean hasPendingToolCalls(LlmResponse response) {
        if (response.getFinishReason() == LlmResponse.FinishReason.TOOL_CALLS) {
            return true;
        }
        LlmMessage message = response.getMessage();
        return message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    /**
     * 命令分发：/goal 带参数时无法被内核的"命令名精确匹配"命中（{@code /goal 修复xx} ≠ {@code /goal}），
     * 因此在命令处理器外包一层，先按前缀转交能力解析，其余命令回落到内核处理器。
     */
    @Override
    protected AgentCommandHandler createCommandHandler(SessionManager sessionManager, SkillLoader skillLoader) {
        AgentCommandHandler base = super.createCommandHandler(sessionManager, skillLoader);
        return new AgentCommandHandler(sessionManager, skillLoader, goal.commands()) {
            @Override
            public boolean handleCommand(String input) {
                if (goal.handleCommand(input)) {
                    return true;
                }
                return super.handleCommand(input);
            }
        };
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        // 基座工具（不含任何能力工具）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, WriteFileDefinition,
                ListFilesDefinition, EditFileDefinition, ContentSearchDefinition));

        // 插拔式集成：复制 capability/goal/ 目录 + 这一行注册
        GoalCapability goal = new GoalCapability(provider, MODEL, sessionManager);
        Agent25 agent = new Agent25(provider, MODEL, baseTools, goal);
        agent.capability(goal);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", goal.promptFragments()));

        System.out.println("=== Goal Loop 智能体（s17） ===");
        System.out.println(goal.description());
        System.out.println();

        // 本模块无技能体系，SkillLoader 传 null（/skills 命令会提示未启用）
        agent.setSessionManager(sessionManager, null);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
