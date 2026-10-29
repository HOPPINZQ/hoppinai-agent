package com.hoppinzq.agent.capability.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.capability.protocol.ProtocolState;
import com.hoppinzq.agent.capability.teams.MailboxMessage;
import com.hoppinzq.agent.capability.teams.MessageBus;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * 团队协议能力（可整体复制的能力包）。
 *
 * <p>在 teams 能力（消息邮箱）之上增加一层<b>协议状态机</b>：
 * lead 与 teammate 之间的请求/回执不再只是普通文本，而是带 requestId 与状态的协议消息
 * （shutdown_request / shutdown_response / plan_approval_request / plan_approval_response）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>3 个工具：{@code request_shutdown} / {@code request_plan} / {@code review_plan}</li>
 *   <li>1 个钩子：{@code beforeLlmCall} 里轮询 lead 邮箱，按消息类型交
 *       {@link ProtocolDispatcher} 路由，把回执与请求以 user 消息注入本轮上下文
 *       （原内核每轮 pollInbox 的职责移入能力，内核不再感知协议）</li>
 * </ul>
 *
 * <p>邮箱是无状态的文件实现，因此本能力自建 {@link MessageBus} 实例即可与 teams 能力读写同一批邮箱文件；两者可以独立注册、
 * 也可以只注册其中一个（此时协议工具仍可用，只是没有 teammate 会回执）。
 *
 * <p>对内核的依赖仅限：Capability 契约、client、ToolDefinition。
 *
 * @author hoppinzq
 */
public class ProtocolCapability implements Capability {

    private final MessageBus bus;
    private final ProtocolRegistry registry = new ProtocolRegistry();
    private final ProtocolDispatcher dispatcher = new ProtocolDispatcher(registry);
    private final ObjectMapper mapper = new ObjectMapper();

    public ProtocolCapability(MessageBus bus) {
        this.bus = bus;
    }

    @Override
    public String name() {
        return "protocol";
    }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new ToolDefinition(
                        "request_shutdown",
                        "向某个 teammate 发送 shutdown_request，并在协议注册表登记一条 pending 状态（带 requestId）。"
                                + "teammate 收到后回 shutdown_response，回执会在下一轮对话里自动出现。",
                        ToolDefinition.createInputSchema(
                                Map.of("teammate", ToolDefinition.createProperty("string", "要关闭的 teammate 名字。")),
                                List.of("teammate")),
                        ShutdownRequestInput.class,
                        this::requestShutdown),
                new ToolDefinition(
                        "request_plan",
                        "让某个 teammate 提交一份方案（发送一条 message 类型消息）。teammate 会以 plan_approval_request 回执。",
                        ToolDefinition.createInputSchema(
                                Map.of(
                                        "teammate", ToolDefinition.createProperty("string", "目标 teammate 名字。"),
                                        "plan", ToolDefinition.createProperty("string", "希望对方按什么方向出方案（可选）。")),
                                List.of("teammate")),
                        RequestPlanInput.class,
                        this::requestPlan),
                new ToolDefinition(
                        "review_plan",
                        "对 teammate 提交的方案做 approve / reject，并通过邮箱回送 plan_approval_response，"
                                + "同时把协议状态标记为 approved / rejected。",
                        ToolDefinition.createInputSchema(
                                Map.of(
                                        "requestId", ToolDefinition.createProperty("string", "方案请求的 requestId。"),
                                        "approved", ToolDefinition.createProperty("boolean", "是否通过。"),
                                        "comment", ToolDefinition.createProperty("string", "审批意见（可选）。")),
                                List.of("requestId", "approved")),
                        ReviewPlanInput.class,
                        this::reviewPlan));
    }

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
                List<MailboxMessage> inbox = bus.readInbox("lead");
                if (inbox.isEmpty()) {
                    return messages;
                }
                // 有新消息才复制列表：无变化时原样返回，避免内核无谓替换上下文
                List<LlmMessage> out = new ArrayList<>(messages);
                for (MailboxMessage msg : inbox) {
                    DispatchOutcome outcome = dispatcher.handle(msg);
                    if (outcome != null && outcome.getUserNotice() != null && !outcome.getUserNotice().isBlank()) {
                        out.add(LlmMessage.user("[protocol] " + outcome.getUserNotice()));
                    }
                }
                return out;
            }
        });
    }

    @Override
    public List<String> promptFragments() {
        return List.of("""
                ## 协议工具
                - request_shutdown(teammate)：让 teammate 优雅退出，对方回 shutdown_response
                - request_plan(teammate, plan)：让 teammate 先出方案
                - review_plan(requestId, approved, comment)：审批方案，对方收到 plan_approval_response
                - 每条协议消息都带 requestId，回执会自动出现在下一轮对话里，无需你主动轮询""");
    }

    public String description() {
        return "协议状态机：shutdown_request / plan_approval 两类请求，registry 记录 pending→responded";
    }

    // ============================== 工具实现 ==============================

    private String requestShutdown(ShutdownRequestInput in) {
        try {
            if (in == null || in.getTeammate() == null || in.getTeammate().isBlank()) {
                return "错误：teammate 不能为空";
            }
            String requestId = registry.newRequestId();
            Map<String, Object> payload = new HashMap<>();
            payload.put("requestId", requestId);
            payload.put("from", "lead");
            registry.create("shutdown_request", "lead", in.getTeammate(), payload);
            bus.send("lead", in.getTeammate(), OBJECT_MAPPER.writeValueAsString(payload), "shutdown_request");
            return "已请求 " + in.getTeammate() + " 关闭 (requestId=" + requestId + ")";
        } catch (Exception e) {
            return "request_shutdown 错误: " + e.getMessage();
        }
    }

    private String requestPlan(RequestPlanInput in) {
        try {
            if (in == null || in.getTeammate() == null || in.getTeammate().isBlank()) {
                return "错误：teammate 不能为空";
            }
            String content = (in.getPlan() == null || in.getPlan().isBlank()) ? "请提交你的计划" : in.getPlan();
            bus.send("lead", in.getTeammate(), content, "message");
            return "已请求 " + in.getTeammate() + " 提交方案";
        } catch (Exception e) {
            return "request_plan 错误: " + e.getMessage();
        }
    }

    private String reviewPlan(ReviewPlanInput in) {
        try {
            if (in == null || in.getRequestId() == null || in.getRequestId().isBlank()) {
                return "错误：requestId 不能为空";
            }
            String status = in.isApproved() ? "approved" : "rejected";
            registry.markResponded(in.getRequestId(), status);

            // 查出原请求的发起方（teammate），把审批结果回送
            String target = "lead";
            ProtocolState state = registry.get(in.getRequestId()).orElse(null);
            if (state != null && state.getSender() != null) {
                target = state.getSender();
            }
            Map<String, Object> respPayload = new HashMap<>();
            respPayload.put("requestId", in.getRequestId());
            respPayload.put("approved", in.isApproved());
            respPayload.put("comment", in.getComment() == null ? "" : in.getComment());
            bus.send("lead", target, OBJECT_MAPPER.writeValueAsString(respPayload), "plan_approval_response");
            return "已" + (in.isApproved() ? "通过" : "驳回") + " " + in.getRequestId() + " 的方案"
                    + (in.getComment() == null ? "" : "：" + in.getComment());
        } catch (Exception e) {
            return "review_plan 错误: " + e.getMessage();
        }
    }
}