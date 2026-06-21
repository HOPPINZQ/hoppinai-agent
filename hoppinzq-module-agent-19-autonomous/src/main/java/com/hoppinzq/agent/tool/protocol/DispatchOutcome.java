package com.hoppinzq.agent.tool.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link ProtocolDispatcher#handle} 的返回值。
 * <ul>
 *   <li>{@link #shouldTerminateAgent}：是否应终止当前 agent（保留位，lead 默认 false）</li>
 *   <li>{@link #shouldTerminateTeammate}：是否应终止某个 teammate（lead 收到 shutdown_response 时为 true）</li>
 *   <li>{@link #userNotice}：要回灌给 LLM 的提示文本，null/空 表示不注入</li>
 * </ul>
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchOutcome {
    private boolean shouldTerminateAgent;
    private boolean shouldTerminateTeammate;
    private String userNotice;
}
