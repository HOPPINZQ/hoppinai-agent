package com.hoppinzq.agent.capability.compact;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.capability.compact.CompactConfig.CONTEXT_LIMIT;
import static com.hoppinzq.agent.capability.compact.CompactConfig.KEEP_HEAD;
import static com.hoppinzq.agent.capability.compact.CompactConfig.KEEP_RECENT;
import static com.hoppinzq.agent.capability.compact.CompactConfig.MAX_BYTES_PER_MESSAGE;
import static com.hoppinzq.agent.capability.compact.CompactConfig.MAX_MESSAGES;
import static com.hoppinzq.agent.capability.compact.CompactConfig.PERSIST_THRESHOLD;

/**
 * 上下文压缩能力（可整体复制的能力包范本）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>1 个工具：{@code compact}（LLM 主动触发手动压缩）</li>
 *   <li>1 个钩子：{@code beforeLlmCall}（每次 LLM 调用前跑压缩管线，超阈值时自动摘要压缩）</li>
 *   <li>3 个命令：{@code /tokens}、{@code /compact}、{@code /transcripts}</li>
 *   <li>系统提示片段：告知模型存在自动压缩</li>
 * </ul>
 *
 * <p>四层压缩策略（cheap first, expensive last，参考Python s08设计）：
 * <ul>
 *   <li>L1: snip_compact       — 裁掉中间消息（消息数 &gt; MAX_MESSAGES）</li>
 *   <li>L2: micro_compact      — 旧 tool_result 替换为占位符（保留最近 KEEP_RECENT 条）</li>
 *   <li>L3: tool_result_budget — 持久化大输出到磁盘（单次消息超限）</li>
 *   <li>L4: auto_compact       — LLM 完整摘要（token 超阈值，1 API 调用）</li>
 *   <li>Emergency: reactive_compact — API 返回 prompt_too_long 时触发</li>
 * </ul>
 *
 * <p>对内核的依赖仅限：Capability 契约、client（协议中立模型）、SessionManager（会话 ID）、
 * AIConstants.ROOT（工作目录）。拔除本目录并删除注册行即可完全移除该能力。
 *
 * @author hoppinzq
 */
public class CompactCapability implements Capability {

    private final ContextCompactor compactor;
    private final TranscriptStore transcriptStore;
    /** onStart 时捕获的宿主 Agent，用于读写当前上下文（命令与工具触发手动压缩） */
    private ZQAgent agent;

    public CompactCapability(LlmProvider provider, String model, SessionManager sessionManager) {
        this.compactor = new ContextCompactor(provider, model, sessionManager);
        this.transcriptStore = new TranscriptStore(
                sessionManager == null ? "default" : sessionManager.getSessionId());
    }

    @Override
    public String name() {
        return "compact";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
    }

    // ============================== 工具 ==============================

    @Override
    public List<ToolDefinition> tools() {
        ToolDefinition compact = new ToolDefinition(
                "compact",
                "手动触发对话压缩。当上下文过大时使用此工具。\n\n" +
                        "使用场景：\n" +
                        "• 对话历史过长，占用大量 token 时\n" +
                        "• 需要保持核心信息但压缩历史对话时\n" +
                        "• 系统提示接近 token 限制时\n\n" +
                        "功能说明：\n" +
                        "• 压缩历史对话，保留关键信息\n" +
                        "• 可选指定压缩重点（focus 参数）\n" +
                        "• 压缩后保留最近的工作上下文",
                ToolDefinition.createInputSchema(
                        java.util.Map.of("focus", ToolDefinition.createProperty(
                                "string", "压缩重点，指定在摘要中保留的内容类型（可选）")),
                        List.of()),
                CompactInput.class,
                this::invokeCompactTool);
        return List.of(compact);
    }

    /**
     * compact 工具执行体：LLM 主动触发手动压缩（原 Tools.compact 静态钩子方案已移除，
     * 工具与压缩器同包后直接调用）。
     */
    private String invokeCompactTool(CompactInput input) {
        try {
            manualCompact(input == null ? null : input.getFocus());
            return "压缩完成。上下文已清理，完整历史已保存。下次对话将使用压缩后的上下文。";
        } catch (Exception e) {
            return "压缩失败: " + e.getMessage();
        }
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
                // 每次调用 LLM 前执行压缩管线（budget → snip → micro → auto）；
                // 返回新列表，由内核替换当前上下文
                return compactor.compactIfNeeded(messages);
            }
        });
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(
                new AgentCommand() {
                    @Override
                    public String name() {
                        return "/tokens";
                    }

                    @Override
                    public String description() {
                        return "显示当前上下文的 token 估算";
                    }

                    @Override
                    public void execute() {
                        System.out.print(compactor.formatTokenStats(agent.getMessageParams()));
                    }
                },
                new AgentCommand() {
                    @Override
                    public String name() {
                        return "/compact";
                    }

                    @Override
                    public String description() {
                        return "手动触发上下文压缩";
                    }

                    @Override
                    public void execute() {
                        manualCompact(null);
                    }
                },
                new AgentCommand() {
                    @Override
                    public String name() {
                        return "/transcripts";
                    }

                    @Override
                    public String description() {
                        return "查看压缩历史记录";
                    }

                    @Override
                    public void execute() {
                        printTranscripts();
                    }
                });
    }

    // ============================== 压缩执行 ==============================

    /**
     * 手动压缩：由 /compact 命令或 compact 工具触发。
     * 摘要生成在 {@link ContextCompactor#autoCompact}；这里用压缩结果原地替换当前上下文。
     */
    private void manualCompact(String focus) {
        List<LlmMessage> context = agent.getMessageParams();
        if (context.isEmpty()) {
            System.out.println("[压缩跳过] 当前上下文为空，无需压缩。");
            return;
        }
        try {
            System.out.printf("[开始手动压缩] 消息数: %d, 估算tokens: %d%n",
                    context.size(), compactor.countTokens(context));
            String reason = focus == null || focus.isBlank() ? "manual" : "manual: " + focus;
            List<LlmMessage> compressed = compactor.autoCompact(new ArrayList<>(context), reason);
            // 原地替换：保持 agent.messageParams 引用不变
            context.clear();
            context.addAll(compressed);
            System.out.println("[压缩完成] 上下文已清理，完整历史已保存。下次对话将使用压缩后的上下文。");
        } catch (Exception e) {
            System.err.println("[压缩失败] " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void printTranscripts() {
        List<TranscriptRecord> records = transcriptStore.list();
        if (records.isEmpty()) {
            System.out.println("[提示] 本次会话暂无压缩记录");
            return;
        }
        System.out.println("========== 压缩历史记录 ==========");
        int idx = 1;
        for (TranscriptRecord t : records) {
            System.out.printf("[%d] %s  原因:%s  消息数:%d  Tokens:%d%n",
                    idx++, t.getTimestamp(), t.getReason(), t.getMessageCount(), t.getEstimatedTokens());
            if (t.getSummary() != null && !t.getSummary().isEmpty()) {
                System.out.printf("摘要: %s%n", t.getSummary().substring(0, Math.min(100, t.getSummary().length())));
                if (t.getSummary().length() > 100) {
                    System.out.println("...");
                }
            }
            System.out.println();
        }
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "- 在对话过程中自动压缩上下文，支持长时间工作\n" +
                "- 当上下文过大时，系统会自动压缩历史对话，保持连贯性；也可用 compact 工具手动触发");
    }

    /** 启动横幅用的配置摘要 */
    public String description() {
        return "四层压缩策略（参考Python s08设计）：\n" +
                "  L1 snip_compact: 最大消息数 = " + MAX_MESSAGES + ", 保留头部 = " + KEEP_HEAD + "\n" +
                "  L2 micro_compact: 保留最近工具结果数 = " + KEEP_RECENT + "\n" +
                "  L3 budget: 持久化阈值 = " + (PERSIST_THRESHOLD / 1024) + "KB, 单次消息最大 = " + (MAX_BYTES_PER_MESSAGE / 1024) + "KB\n" +
                "  L4 auto_compact: token阈值 = " + CONTEXT_LIMIT;
    }
}
