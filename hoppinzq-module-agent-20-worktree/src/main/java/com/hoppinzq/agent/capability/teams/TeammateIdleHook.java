package com.hoppinzq.agent.capability.teams;

/**
 * teammate 空闲回调（可选扩展点）。
 *
 * <p>teammate 主循环每轮开始时先消费自己的邮箱；若<b>本轮没有任何新输入</b>，
 * 就调用本回调询问"还有没有活可干"：
 * <ul>
 *   <li>返回非空文本 → 作为一条 user 消息注入对话，teammate 继续工作</li>
 *   <li>返回 null → 认为无事可做，teammate 结束（走正常收尾流程，给 lead 发完成消息）</li>
 * </ul>
 *
 * <p>这是 autonomous 能力（任务板 + 自动认领）接入 teams 的唯一钩子，
 * 也让 teams 保持不感知具体调度策略。
 *
 * @author hoppinzq
 */
public interface TeammateIdleHook {

    /**
     * @param teammateName 当前 teammate 名字
     * @return 注入下一轮的 user 文本；null 表示结束
     */
    String onIdle(String teammateName);
}
