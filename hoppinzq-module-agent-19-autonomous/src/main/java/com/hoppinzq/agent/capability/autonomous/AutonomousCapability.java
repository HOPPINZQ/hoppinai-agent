package com.hoppinzq.agent.capability.autonomous;

import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.capability.task.TaskInput;
import com.hoppinzq.agent.capability.task.TaskManager;
import com.hoppinzq.agent.capability.teams.MessageBus;
import com.hoppinzq.agent.capability.teams.TeammateIdleHook;

import java.util.List;

/**
 * 自主认领能力（可整体复制的能力包）。
 *
 * <p>它实现 teams 能力的 {@link TeammateIdleHook}：teammate 每轮开始、邮箱为空时，
 * 由本能力去任务板找活 —— 有未认领任务就自动认领并把任务详情交回 teammate 继续工作，
 * 连续 {@link AutonomousConfig#POLL_TIMEOUT_MS} 都没有活才返回 null（teammate 退出）。
 *
 * <p>与 teams 的接入方式（装配侧一行）：
 * <pre>{@code
 *   TeamsCapability teams = new TeamsCapability(...);
 *   AutonomousCapability auto = new AutonomousCapability(taskCapability.getTaskManager());
 *   teams.idleHook(auto);
 * }</pre>
 * teams 因此完全不感知任务板，autonomous 也可以拔掉换成别的调度策略。
 *
 * <p>对内核的依赖仅限：Capability 契约；对同批能力包依赖：teams（邮箱/空闲钩子）、task（任务板）。
 *
 * @author hoppinzq
 */
public class AutonomousCapability implements Capability, TeammateIdleHook {

    private final TaskManager taskManager;
    private final MessageBus bus = new MessageBus();

    public AutonomousCapability(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public String name() {
        return "autonomous";
    }

    @Override
    public List<String> promptFragments() {
        return List.of("""
                ## 自主认领
                teammate 在空闲时会自动轮询任务板（create_task 产生的未认领任务），
                认领后进入工作阶段，完成后自动 complete_task；连续 60 秒无任务则自行退出。""");
    }

    /**
     * teammate 空闲回调：轮询任务板 → 自动认领 → 返回任务说明。
     *
     * @return 非空 = 有新活，作为 user 消息注入 teammate 对话；null = 无活可干，teammate 退出
     */
    @Override
    public String onIdle(String teammateName) {
        long deadline = System.currentTimeMillis() + AutonomousConfig.POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            // 1. 任务板有可认领任务 → 认领并交给 teammate
            List<TaskInput> ready = taskManager.scanUnclaimedTasks();
            if (!ready.isEmpty()) {
                TaskInput first = ready.get(0);
                try {
                    taskManager.claimTask(first.getId(), teammateName);
                } catch (Exception e) {
                    System.err.println("[autonomous] 认领任务失败: " + e.getMessage());
                    return null;
                }
                StringBuilder prompt = new StringBuilder("[autonomous] 已认领任务 ");
                prompt.append(first.getDisplayString());
                if (first.getDescription() != null && !first.getDescription().isEmpty()) {
                    prompt.append("\n描述：").append(first.getDescription());
                }
                prompt.append("\n完成后请调用 complete_task(").append(first.getId()).append(")。");
                System.out.printf("[autonomous] %s 认领任务 #%d%n", teammateName, first.getId());
                return prompt.toString();
            }
            try {
                Thread.sleep(AutonomousConfig.POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public String description() {
        return "自主认领：teammate 空闲时轮询任务板并自动 claim，连续 "
                + (AutonomousConfig.POLL_TIMEOUT_MS / 1000) + " 秒无任务则退出";
    }
}