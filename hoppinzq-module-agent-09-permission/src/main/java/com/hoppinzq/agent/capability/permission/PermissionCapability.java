package com.hoppinzq.agent.capability.permission;

import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;

import java.util.List;
import java.util.Scanner;

/**
 * 权限护栏能力（可整体复制的能力包）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>1 个钩子：{@code beforeToolCall} —— 所有工具调用先过三重闸门
 *       （denyList → rules → askUser），返回非 null 文案即拦截本次调用，
 *       该文案作为工具结果回灌给模型。这是内核 {@link AgentHook} 契约的标准用法，
 *       取代了旧版在 ZQAgent 里硬编码 {@code permissionChecker} 字段的静态接线。</li>
 *   <li>系统提示片段：告知模型存在权限闸门以及被拒后的应对方式</li>
 * </ul>
 *
 * <p>三重闸门（对应 Python 教程 s03_permission）：
 * <ol>
 *   <li>命中黑名单（rm -rf /、sudo、format、Windows del /f /s /q C:\ 等）→ 直接拒绝</li>
 *   <li>规则检查（write_file/edit_file/read_file 路径不能越出 ROOT）→ 拒绝</li>
 *   <li>破坏性命令（rm / del / git push --force 等）→ 交互式 y/N 确认</li>
 * </ol>
 *
 * <p>本能力不注册任何工具：权限是横切关注点（cross-cutting concern），
 * 作用于其它所有工具的执行前，这正是钩子挂载点与工具注册的本质区别。
 *
 * <p>对内核的依赖仅限：Capability 契约、client（ToolCall 模型）。
 *
 * @author hoppinzq
 */
public class PermissionCapability implements Capability {

    /** 三重闸门流水线（ASK 闸门需要与主循环共用的控制台 Scanner） */
    private final PermissionChecker checker;

    public PermissionCapability(Scanner scanner) {
        this.checker = new PermissionChecker(scanner);
    }

    @Override
    public String name() {
        return "permission";
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public String beforeToolCall(LlmMessage.ToolCall call) {
                Decision decision = checker.check(call.getName(), call.getArgumentsJson());
                switch (decision.getType()) {
                    case DENY:
                        // 非 null 返回值 = 拦截：内核跳过真实执行，把该文案作为工具结果回灌
                        return PermissionConfig.DENY_MESSAGE_PREFIX + decision.getReason();
                    case ALLOW:
                    case ASK:
                    default:
                        // ASK 已在 checker 内部完成交互式确认（用户同意 → ALLOW）
                        return null;
                }
            }
        });
    }

    // ============================== 提示词片段 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of("""
                ## 权限护栏

                你的任何工具调用都会先经过三重权限闸门：
                1) 危险命令黑名单（rm -rf /、sudo、format 等）
                2) 路径越界规则（所有文件操作必须在工作目录内）
                3) 破坏性命令需用户交互式确认
                如果工具被拒绝，你会收到以 "%s" 开头的工具结果，请如实告知用户并建议替代方案。
                注意：不要尝试绕过权限规则；如果用户请求被拦，请解释原因。"""
                .formatted(PermissionConfig.DENY_MESSAGE_PREFIX));
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "三重权限闸门: denyList(" + PermissionRules.DENY_LIST.size() + " 条黑名单正则) → "
                + "rules(路径越界) → askUser(" + PermissionRules.DESTRUCTIVE_PATTERNS.size() + " 类破坏性命令需确认)";
    }
}
