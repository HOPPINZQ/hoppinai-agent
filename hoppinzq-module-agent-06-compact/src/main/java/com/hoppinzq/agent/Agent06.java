package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 上下文压缩智能体
 *
 * <p>本类只负责<b>装配</b>：压缩策略（L1~L4）与执行顺序（cheap first, expensive last）
 * 全部收拢在 {@link ContextCompactor}（tool.compact 包），Agent 每轮只需调用
 * {@link ContextCompactor#compactIfNeeded(List)} 一个入口。
 *
 * <p>四层压缩策略（参考Python s08设计）：
 * <ul>
 *   <li>L1: snip_compact       — 裁掉中间消息（消息数 &gt; {@value com.hoppinzq.agent.constant.AIConstants#MAX_MESSAGES}）</li>
 *   <li>L2: micro_compact      — 旧 tool_result 替换为占位符（保留最近 3 条）</li>
 *   <li>L3: tool_result_budget — 持久化大输出到磁盘（单次消息超限）</li>
 *   <li>L4: auto_compact       — LLM 完整摘要（token 超阈值，1 API 调用）</li>
 *   <li>Emergency: reactive_compact — API 返回 prompt_too_long 时触发</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class Agent06 extends ZQAgent {

    /**
     * 工具定义（{@code ToolDefinition} 静态字段）在类初始化时读取这两个实例，
     * 必须在构建工具列表之前赋值。
     */
    public static SkillLoader skillLoader;
    public static TodoManager todoManager;

    private final ContextCompactor compactor;

    public Agent06(LlmProvider provider, String model, List<ToolDefinition> tools, ContextCompactor compactor) {
        super(provider, model, tools);
        this.compactor = compactor;
        // 注册手动压缩钩子：compact 工具（静态定义）通过 ContextCompactor.manualCompactHook 触发
        ContextCompactor.manualCompactHook = this::manualCompact;
    }

    /**
     * 注册 /tokens 与 /compact 命令；统计报告与压缩逻辑都在 ContextCompactor。
     */
    @Override
    protected AgentCommandHandler createCommandHandler(SessionManager sessionManager, SkillLoader skillLoader) {
        Runnable tokensHandler = () -> System.out.print(compactor.formatTokenStats(this.messageParams));
        Runnable compactHandler = this::manualCompact;
        return new AgentCommandHandler(sessionManager, skillLoader, tokensHandler, compactHandler);
    }

    /**
     * 手动压缩：由 /compact 命令或 compact 工具触发。
     * 摘要生成在 {@link ContextCompactor#autoCompact}；这里只负责用压缩结果替换当前上下文。
     */
    private void manualCompact() {
        try {
            if (this.messageParams.isEmpty()) {
                System.out.println("[压缩跳过] 当前上下文为空，无需压缩。");
                return;
            }
            System.out.printf("[开始手动压缩] 消息数: %d, 估算tokens: %d%n",
                    messageParams.size(), compactor.countTokens(messageParams));
            replaceMessageParams(compactor.autoCompact(new ArrayList<>(messageParams), "manual"));
            System.out.println("[压缩完成] 上下文已清理，完整历史已保存到会话。下次对话将使用压缩后的上下文。");
        } catch (Exception e) {
            System.err.println("[压缩失败] " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 每次调用 LLM 前执行压缩管线（budget → snip → micro → auto），
     * 编排在 {@link ContextCompactor#compactIfNeeded(List)}。
     */
    @Override
    protected LlmResponse chatMessage(List<LlmMessage> messageParams) {
        // messageParams 与父类字段是同一引用，压缩结果须同步回写
        replaceMessageParams(compactor.compactIfNeeded(messageParams));
        return super.chatMessage(this.messageParams);
    }

    /**
     * 替换消息列表
     */
    private void replaceMessageParams(List<LlmMessage> newParams) {
        this.messageParams.clear();
        this.messageParams.addAll(newParams);
    }

    /**
     * 生成Agent06的系统提示词
     *
     * @param root 工作目录
     * @param skillDescriptions 技能描述
     * @return 完整的系统提示词
     */
    public static String generateSystemPrompt(String root, String skillDescriptions) {
        return String.format(
                "你是一个具有上下文压缩能力的编程智能体，工作在目录: %s\n\n" +
                "核心能力：\n" +
                "- 使用工具读写文件、编辑代码、执行命令、搜索内容\n" +
                "- 通过 todo 工具管理任务列表，追踪复杂任务进度\n" +
                "- 在对话过程中自动压缩上下文，支持长时间工作\n" +
                "- 加载专业技能以应对特定领域的任务\n\n" +
                "工作指南：\n" +
                "1. 优先使用工具解决任务，而非仅提供建议\n" +
                "2. 在处理复杂或不熟悉的主题时，使用 load_skill 工具获取专业知识\n" +
                "3. 使用 todo 工具将大任务分解为小步骤，逐个完成\n" +
                "4. 当上下文过大时，系统会自动压缩历史对话，保持连贯性\n\n" +
                "可用的技能：\n%s",
                root,
                skillDescriptions
        );
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 工具定义静态初始化依赖这两个实例，须先创建
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);
        ContextCompactor compactor = new ContextCompactor(provider, MODEL, sessionManager);

        List<ToolDefinition> tools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, EditFileDefinition, WriteFileDefinition,
                GlobDefinition, ContentSearchDefinition, SubAgentDefinition, TodoDefinition,
                SkillsDefinition, ContentCompactDefinition));

        Agent06 agent = new Agent06(provider, MODEL, tools, compactor);
        agent.setSystemPrompt(generateSystemPrompt(ROOT, skillLoader.getDescriptions()));

        System.out.println("=== 上下文压缩智能体 ===");
        System.out.println("四层压缩策略（参考Python s08设计）：");
        System.out.println("  L1 snip_compact: 最大消息数 = " + MAX_MESSAGES + ", 保留头部 = " + KEEP_HEAD);
        System.out.println("  L2 micro_compact: 保留最近工具结果数 = " + KEEP_RECENT);
        System.out.println("  L3 budget: 持久化阈值 = " + (PERSIST_THRESHOLD / 1024) + "KB, 单次消息最大 = " + (MAX_BYTES_PER_MESSAGE / 1024) + "KB");
        System.out.println("  L4 auto_compact: token阈值 = " + CONTEXT_LIMIT);
        System.out.println();

        agent.setSessionManager(sessionManager, skillLoader);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
