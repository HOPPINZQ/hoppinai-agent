package com.hoppinzq.agent.capability.goal;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.session.SessionManager;

import java.util.List;

import static com.hoppinzq.agent.capability.goal.GoalConfig.GOAL_STOP_HOOK_BLOCK_CAP;

/**
 * Goal Loop 能力（可整体复制的能力包，对应 Python s17）。
 *
 * <p>核心思想：模型不再调用工具只代表这一轮<b>想停</b>；目标是否完成，
 * 交给 {@link GoalController}（{@link PromptGoalEvaluator} 的一次独立、无工具的模型调用）裁决。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>1 个命令：{@code /goal}（查看状态 / 设置完成条件 / clear 别名清除）</li>
 *   <li>Goal Gate 判断入口：{@link #evaluateAfterTurn(List)}，由宿主
 *       {@code Agent25} 在循环退出边界覆写 {@code chatMessage} 调用（主循环改写按规约保留在子类覆写中）</li>
 *   <li>系统提示片段：告知模型"对话里没有的证据等于不存在"</li>
 * </ul>
 *
 * <p>对内核的依赖仅限：Capability 契约、client（协议中立模型）、session（token 统计）。
 * 拔除本目录、删除 {@code Agent25} 里的 Gate 覆写与 /goal 分发即可完全移除该能力。
 *
 * @author hoppinzq
 */
public class GoalCapability implements Capability {

    private final GoalController controller;
    private final LlmProvider provider;
    private final String evaluatorModel;
    /** 会话管理器（可空）：仅用于 /goal 状态里的 token 消耗统计 */
    private final SessionManager sessionManager;
    /** onStart 时捕获的宿主 Agent */
    private ZQAgent agent;

    public GoalCapability(LlmProvider provider, String model, SessionManager sessionManager) {
        this.provider = provider;
        this.evaluatorModel = GoalConfig.evaluatorModel(model);
        this.sessionManager = sessionManager;
        this.controller = new GoalController(new PromptGoalEvaluator(provider, evaluatorModel), GOAL_STOP_HOOK_BLOCK_CAP);
    }

    @Override
    public String name() {
        return "goal";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
    }

    // ============================== Goal Gate（宿主循环调用） ==============================

    /** 是否有活跃目标（宿主在 Gate 前先检查，避免无目标时的额外判断开销） */
    public boolean hasActiveGoal() {
        return controller.hasActiveGoal();
    }

    /**
     * Goal Gate：本轮模型不再调用工具时判断目标是否达成。
     * <p>本模块没有后台子系统，{@code backgroundRunning} 恒为 false；
     * 宿主若有后台任务，应在此传入真实状态（未结束时判断器 defer）。
     *
     * @return 裁决；{@code block} 时宿主应把 {@link #continueMessage(GoalDecision)}
     *         追加进对话并继续下一轮
     */
    public GoalDecision evaluateAfterTurn(List<LlmMessage> messages) {
        return controller.evaluateAfterTurn(messages, false);
    }

    /** 构造回灌对话的继续消息（block 时使用） */
    public String continueMessage(GoalDecision decision) {
        return controller.continueMessage(decision);
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/goal";
            }

            @Override
            public String description() {
                return "查看/设置/清除完成条件：/goal | /goal <条件> | /goal clear";
            }

            @Override
            public void execute() {
                // execute() 拿不到原始参数行；带参形式由宿主命令分发按前缀转交 handleCommand
                System.out.println(controller.status(totalTokens()));
            }
        });
    }

    /**
     * /goal 命令入口（含参数）。由宿主在命令分发处按前缀转交
     * （内核命令按名称精确匹配，{@code /goal <条件>} 命中不了注册名 {@code /goal}）：
     * <ul>
     *   <li>{@code /goal}                 —— 查看当前条件、判断次数、耗时、token 消耗</li>
     *   <li>{@code /goal clear}（及 stop/off/reset/none/cancel）—— 清除目标</li>
     *   <li>{@code /goal <条件>}           —— 设置完成条件</li>
     * </ul>
     *
     * @return 是否已处理（true 时外层循环继续等下一条输入）
     */
    public boolean handleCommand(String input) {
        if (!input.startsWith("/goal")) {
            return false;
        }
        String rest = input.substring("/goal".length()).trim();
        if (rest.isEmpty()) {
            System.out.println(controller.status(totalTokens()));
            return true;
        }
        if (GoalController.CLEAR_ALIASES.contains(rest.toLowerCase())) {
            System.out.println(controller.clear("cleared"));
            return true;
        }
        try {
            controller.setGoal(rest, totalTokens());
        } catch (GoalException e) {
            System.out.println("[goal] 设置失败: " + e.getMessage());
            return true;
        }
        // 与原入口"设置即开始"不同：内核命令处无法驱动主循环，目标从下一次用户输入起生效，
        // 判断器会在每轮退出边界自动检查；这里只提示如何开始
        System.out.println("[goal] 目标已设置: " + rest);
        System.out.println("[goal] 请输入任务描述开始工作；每轮模型停下时判断器会检查目标，未达成会让你继续。");
        return true;
    }

    /** 会话累计 token（供 Goal 状态统计） */
    private long totalTokens() {
        return sessionManager != null ? sessionManager.getTotalTokens() : 0;
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "- 如果设置了 Goal（完成条件），未达成前独立判断器会让你继续工作\n" +
                "- 运行验证命令后，把命令和退出码/结果明确写进对话，让独立判断器能够检查；\n" +
                "  判断器只能读对话，对话里没有的证据等于不存在");
    }

    /** 启动横幅用的能力摘要 */
    public String description() {
        return "Goal Loop: 模型不再调用工具 ≠ 任务完成，完成条件由独立判断器裁决\n" +
                "  判断器模型: " + evaluatorModel + "\n" +
                "  block 上限: " + GOAL_STOP_HOOK_BLOCK_CAP
                + " | 连续续轮上限: " + (GoalConfig.MAX_TURNS > 0 ? GoalConfig.MAX_TURNS : "不限制") + "\n" +
                "  命令: /goal 查看 | /goal <条件> 设置 | /goal clear 清除";
    }

    /** 供宿主打印诊断信息（如需） */
    public LlmProvider getProvider() {
        return provider;
    }
}
