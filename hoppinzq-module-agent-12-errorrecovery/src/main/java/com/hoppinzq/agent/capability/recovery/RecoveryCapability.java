package com.hoppinzq.agent.capability.recovery;

import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;

import java.util.List;
import java.util.function.Function;

/**
 * 错误恢复能力（对应 Python 教程 s11_error_recovery）。
 *
 * <p>三条恢复路径：
 * <ol>
 *   <li>输出截断（finish_reason == LENGTH）→ maxTokens 8K 升 64K 续写</li>
 *   <li>上下文溢出（prompt_too_long）→ {@link ReactiveCompactor} 紧急压缩历史</li>
 *   <li>瞬态错误（429 / 529 / 网络抖动）→ 指数退避；连续 529 达阈值切 {@link RecoveryConfig#FALLBACK_MODEL}</li>
 * </ol>
 *
 * <p><b>为什么挂在 Provider 层而不是钩子层：</b>重试必须"包住"provider 调用本身，
 * 而内核唯一在调用前分发的钩子 {@code beforeLlmCall} 只能改写上下文、无法接管调用；
 * 若为此给 {@code AgentHook} 追加 {@code onLlmError} 需要同步改内核 catch 块。
 * 故本能力选择不改契约的方案——把恢复逻辑封装成一个装饰器 Provider
 * （{@link #provider()}），由入口类在构造 Agent 前包装原始 Provider。
 * 内核 chatMessage 传入的 {@code request.messages} 与 Agent 上下文列表同引用，
 * 因此 reactive compact 的原地压缩能持久生效（后续轮次使用压缩后的历史）。
 *
 * <p>拔除方式：删除本目录 + 入口类改为直接使用原始 Provider 即可。
 *
 * @author hoppinzq
 */
public class RecoveryCapability implements Capability {

    private final RetryWrapper wrapper;
    private final RecoveryState state;

    public RecoveryCapability(LlmProvider baseProvider, String model, String fallbackModel) {
        this.wrapper = new RetryWrapper(baseProvider);
        this.state = new RecoveryState(model, fallbackModel);
    }

    /**
     * 返回带恢复策略的 Provider。内核的 provider 字段是 final，
     * 因此包装必须发生在构造 Agent 之前（见入口类装配顺序）。
     */
    public LlmProvider provider() {
        return new LlmProvider() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                return wrapper.call(requestBuilderFor(request), state, request.getMessages());
            }
        };
    }

    /** 重试时按恢复状态调整请求：model 可切兜底，maxTokens 由 wrapper 以 state 为准覆盖 */
    private Function<RecoveryState, LlmRequest> requestBuilderFor(LlmRequest request) {
        return current -> {
            request.setModel(current.getModel());
            return request;
        };
    }

    @Override
    public String name() {
        return "recovery";
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        // 恢复逻辑全部在 Provider 装饰器内完成，无需占用任何钩子挂载点；
        // 这里保留空实现以表明该能力对主循环零侵入
        return List.of();
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/recovery";
            }

            @Override
            public String description() {
                return "查看错误恢复状态（maxTokens / 重试 / 兜底模型）";
            }

            @Override
            public void execute() {
                System.out.println("========== 错误恢复状态 ==========");
                System.out.println("当前模型: " + state.getModel()
                        + (state.isSwitchedToFallback() ? "（已从 " + state.getFallbackModel() + " 切换）" : ""));
                System.out.println("输出上限: " + state.getMaxTokens() + " tokens"
                        + "（初始 " + RecoveryState.INITIAL_MAX_TOKENS
                        + "，截断时升级到 " + RecoveryState.UPGRADED_MAX_TOKENS + "）");
                System.out.println("连续 529 次数: " + state.getConsecutive529().get()
                        + "（阈值 " + RecoveryState.OVERLOAD_SWITCH_THRESHOLD + " 切兜底）");
                System.out.println("退避策略: min(" + RecoveryConfig.BASE_DELAY_MS + " × 2^n, "
                        + RecoveryConfig.MAX_DELAY_MS + "ms) + jitter");
            }
        });
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "- 系统带错误恢复能力：输出截断时自动升级 maxTokens 并继续生成；上下文溢出时紧急压缩历史；瞬态错误时指数退避重试\n" +
                "- 如果某轮回复被截断，请尝试在下一轮继续，不要重复已输出的内容");
    }

    /** 启动横幅用的能力摘要 */
    public String description() {
        return "错误恢复（三条路径）：\n" +
                "  输出截断: maxTokens " + RecoveryState.INITIAL_MAX_TOKENS + " → "
                    + RecoveryState.UPGRADED_MAX_TOKENS + " 续写\n" +
                "  上下文溢出: prompt_too_long 时保留首条 + 最近 " + RecoveryConfig.KEEP_RECENT + " 条\n" +
                "  瞬态错误: 指数退避 min(" + RecoveryConfig.BASE_DELAY_MS + "×2^n, "
                    + RecoveryConfig.MAX_DELAY_MS + "ms)，连续 529 "
                    + RecoveryState.OVERLOAD_SWITCH_THRESHOLD + " 次切兜底模型";
    }
}
