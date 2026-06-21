package com.hoppinzq.agent.tool.autonomous;

import com.hoppinzq.agent.tool.schema.TaskInput;
import com.hoppinzq.agent.tool.task.TaskManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 自动认领器。Teammate 在进入 WORK 阶段前调用 {@link #tryClaim}，
 * 让 teammate 自动从任务板上抢一个可认领任务。
 *
 * <p>和 {@link IdlePoller} 的区别：
 * <ul>
 *   <li>{@code IdlePoller.poll} 只负责「发现」工作（返回任务摘要给 teammate 当作 user 输入）</li>
 *   <li>{@code AutoClaimer.tryClaim} 负责真正调用 {@link TaskManager#claimTask} 改状态、写 owner</li>
 * </ul>
 * 二者配合：poll 触发 WORK 阶段后，teammate 主循环第一件事就是 tryClaim，
 * 然后把任务详情注入对话让 LLM 开干。
 *
 * @author hoppinzq
 */
@Slf4j
public class AutoClaimer {

    /**
     * 尝试认领第一个可认领的任务。
     *
     * @param owner   认领者名字（teammate name）
     * @param tm      TaskManager
     * @param context 用于把认领到的任务摘要追加进来的 StringBuilder（teammate 会把它注入到对话里）
     * @return true=成功认领到任务；false=没有可认领的任务
     */
    public boolean tryClaim(String owner, TaskManager tm, StringBuilder context) {
        var ready = tm.scanUnclaimedTasks();
        if (ready.isEmpty()) {
            return false;
        }
        TaskInput first = ready.get(0);
        try {
            tm.claimTask(first.getId(), owner);
            context.append("[autonomous] 已认领任务 ").append(first.getDisplayString());
            if (first.getDescription() != null && !first.getDescription().isEmpty()) {
                context.append("\n描述：").append(first.getDescription());
            }
            log.info("[{}] 自动认领任务 #{}", owner, first.getId());
            return true;
        } catch (Exception e) {
            // 可能被其它 teammate 抢先认领了，记录后返回 false
            log.warn("[{}] 认领任务 #{} 失败: {}", owner, first.getId(), e.getMessage());
            return false;
        }
    }
}
