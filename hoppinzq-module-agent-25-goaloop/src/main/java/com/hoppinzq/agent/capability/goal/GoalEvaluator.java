package com.hoppinzq.agent.capability.goal;

import com.hoppinzq.agent.client.LlmMessage;

import java.util.List;

/**
 * Goal 判断器：读取对话记录，判断完成条件是否已由证据满足。
 * <p>判断器是独立于主模型的一次调用，没有工具、不能读文件、
 * 只能根据对话中已经出现的内容做判断。
 *
 * @author hoppinzq
 */
@FunctionalInterface
public interface GoalEvaluator {

    /**
     * @param condition 完成条件
     * @param messages  到目前为止的对话记录
     * @return 判断结果
     * @throws GoalException 判断器返回不合法或调用失败时
     */
    GoalEvaluation evaluate(String condition, List<LlmMessage> messages);
}
