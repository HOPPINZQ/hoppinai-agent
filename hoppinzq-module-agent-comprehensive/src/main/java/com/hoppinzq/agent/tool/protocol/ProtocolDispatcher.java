package com.hoppinzq.agent.tool.protocol;

import com.hoppinzq.agent.tool.bus.MailboxMessage;

import java.util.Map;
import java.util.Optional;

/**
 * Lead 侧的消息分发器：根据 inbox 消息的 type 路由处理。
 * <p>
 * 处理的消息类型：
 * <ul>
 *   <li>{@code message}：普通消息，回灌给 LLM</li>
 *   <li>{@code shutdown_response}：teammate 对 lead 早前 shutdown_request 的回执，登记为 responded</li>
 *   <li>{@code plan_approval_request}：teammate 提交的方案审批请求，登记 pending 等待 lead 调用 review_plan</li>
 *   <li>其它：当作普通 message 处理</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class ProtocolDispatcher {

    private final ProtocolRegistry registry;

    public ProtocolDispatcher(ProtocolRegistry registry) {
        this.registry = registry;
    }

    /**
     * 处理一条 inbox 消息。
     *
     * @param msg 收到的 mailbox 消息
     * @return 处理结果，包含是否终止、提示文本等
     */
    public DispatchOutcome handle(MailboxMessage msg) {
        if (msg == null) {
            return DispatchOutcome.builder().build();
        }
        String type = msg.getType() == null ? "message" : msg.getType();
        switch (type) {
            case "shutdown_response": {
                String requestId = extractRequestId(msg.getContent());
                if (requestId != null) {
                    registry.markResponded(requestId, "responded");
                }
                System.out.printf("\u001b[95m[protocol]\u001b[0m 收到 %s 的 shutdown_response（requestId=%s），teammate 已退出%n",
                        msg.getFrom(), requestId);
                return DispatchOutcome.builder()
                        .shouldTerminateTeammate(true)
                        .userNotice("teammate " + msg.getFrom() + " 已确认关闭 (requestId=" + requestId + ")")
                        .build();
            }
            case "plan_approval_request": {
                // teammate 把 requestId 放在 content（JSON 字符串）里
                String requestId = extractRequestId(msg.getContent());
                String plan = extractField(msg.getContent(), "plan");
                if (requestId == null) {
                    requestId = registry.newRequestId();
                }
                registry.register(requestId, "plan_approval_request", msg.getFrom(), "lead",
                        planToPayload(requestId, msg.getFrom(), plan));
                System.out.printf("\u001b[95m[protocol]\u001b[0m teammate %s 请求审批 plan：%s（requestId=%s）%n",
                        msg.getFrom(), plan, requestId);
                return DispatchOutcome.builder()
                        .userNotice("teammate " + msg.getFrom() + " 请求审批 plan："
                                + plan + "（requestId=" + requestId
                                + "），请用 review_plan 工具决定 approve/reject")
                        .build();
            }
            case "message":
            default: {
                System.out.printf("\u001b[95m[protocol]\u001b[0m %s -> lead: %s%n", msg.getFrom(), msg.getContent());
                return DispatchOutcome.builder()
                        .userNotice("收到来自 " + msg.getFrom() + " 的消息：" + msg.getContent())
                        .build();
            }
        }
    }

    private static String extractRequestId(String content) {
        return extractField(content, "requestId");
    }

    /** 从 JSON 字符串里取一个字符串字段；非 JSON 或字段缺失返回 null。 */
    @SuppressWarnings("unchecked")
    private static String extractField(String content, String field) {
        if (content == null || content.isBlank()) return null;
        try {
            Map<String, Object> map = com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER.readValue(content, Map.class);
            Object v = map.get(field);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static java.util.Map<String, Object> planToPayload(String requestId, String from, String plan) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("requestId", requestId);
        payload.put("from", from);
        payload.put("plan", plan);
        return payload;
    }
}
