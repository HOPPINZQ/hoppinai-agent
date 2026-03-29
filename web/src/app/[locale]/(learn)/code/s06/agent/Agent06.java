package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.compact.ContextCompactor;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.manager.TodoManager;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.*;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 上下文压缩智能体
 *
 * 三层压缩策略：
 * - Layer 1: micro_compact - 每次调用前将旧tool_result替换为占位符
 * - Layer 2: auto_compact - token超过阈值时自动压缩
 * - Layer 3: manual compact - 通过compact工具手动压缩
 *
 * @author hoppinzq
 */
public class Agent06 extends ZQAgent {
    public static ContextCompactor compactor;
    public static boolean manualCompactRequested = false;
    public static TodoManager todoManager;
    private int roundsSinceTodo = 0;
    private long lastTodoVersion = 0;
    public static SkillLoader skillLoader;

    public Agent06(AnthropicClient client, String model, List<ToolDefinition> tools, ContextCompactor compactor, SkillLoader skillLoader, TodoManager todoManager) {
        super(client, model, tools);
        Agent06.compactor = compactor;
        Agent06.skillLoader = skillLoader;
        Agent06.todoManager = todoManager;
        this.lastTodoVersion = todoManager.getVersion();
    }

    /**
     * 重写chatMessage方法，实现两层压缩策略
     *
     * 压缩流程：
     * 1. Layer 1（微压缩）：每次LLM调用前自动执行，将旧工具结果替换为占位符
     * 2. Layer 2（自动压缩）：当token估算超过阈值时，保存完整对话并生成摘要
     *
     * 状态同步说明：
     * - ZQAgent将messageParams字段直接传递给此方法（引用传递）
     * - 微压缩返回新列表，自动压缩返回新列表
     * - 发生自动压缩时，需要同步更新ZQAgent的状态
     *
     * @param messageParams 原始消息参数列表
     * @return LLM响应消息
     */
    @Override
    protected Message chatMessage(List<MessageParam> messageParams) {
        // Layer 1: 微压缩 - 每次LLM调用前执行，静默压缩旧的工具结果
        List<MessageParam> compactedParams = compactor.microCompact(new ArrayList<>(messageParams));

        // Layer 2: 自动压缩 - 当token估算超过阈值时触发
        if (ContextCompactor.estimateTokens(compactedParams) > TOKEN_THRESHOLD) {
            System.out.println("[自动压缩已触发]");
            compactedParams = compactor.autoCompact(compactedParams);

            // 【状态同步】需要将压缩后的消息同步到ZQAgent的messageParams字段
            // 说明：
            // - ZQAgent维护messageParams作为状态
            // - chatMessage接收的是messageParams字段的引用
            // - 但microCompact和autoCompact都返回新列表，不会修改原列表
            // - 因此需要手动将压缩后的列表同步回ZQAgent的状态

            // 更新ZQAgent的消息状态，确保下次调用使用压缩后的上下文
            this.messageParams.clear();
            this.messageParams.addAll(compactedParams);
        }

        // 使用（可能被压缩的）消息参数调用父类的chatMessage
        return super.chatMessage(compactedParams);
    }

    /**
     * 重写onToolExecution方法，实现压缩策略和待办事项提醒
     *
     * 功能包括：
     * 1. 待办事项提醒：跟踪更新间隔，超过3回合未更新时提醒
     * 2. Layer 1 微压缩：每次工具执行后静默压缩
     * 3. Layer 2 自动压缩：token超阈值时触发完整压缩
     * 4. Layer 3 手动压缩：响应用户的压缩请求
     *
     * @param toolResults 工具执行结果列表
     */
    @Override
    protected void onToolExecution(List<ContentBlockParam> toolResults) {
        // ========== 待办事项提醒逻辑 ==========
        long currentVersion = todoManager.getVersion();
        if (currentVersion > lastTodoVersion) {
            // 检测到待办列表已更新，重置计数器
            roundsSinceTodo = 0;
            lastTodoVersion = currentVersion;
        } else {
            // 待办列表未更新，增加回合计数
            roundsSinceTodo++;
        }

        // 如果超过3个回合未更新待办，添加提醒消息
        if (roundsSinceTodo >= 3) {
            String reminder = String.format("<reminder>\n您已经 %d 个回合没有更新待办事项列表了。请更新列表以反映当前进度。\n</reminder>", roundsSinceTodo);
            toolResults.add(ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(reminder)
                    .build()));
        }

        // ========== 三层压缩策略 ==========
        // Layer 1: 每次工具执行后进行微压缩（静默、频繁）
        compactor.microCompact(messageParams);

        // Layer 2: 检查是否需要自动压缩（超阈值触发）
        if (compactor.estimateTokens(messageParams) > TOKEN_THRESHOLD) {
            System.out.println("[自动压缩已触发]");
            List<MessageParam> compressed = compactor.autoCompact(messageParams);
            messageParams.clear();
            messageParams.addAll(compressed);
        }

        // Layer 3: 检查是否请求手动压缩（用户主动调用）
        if (manualCompactRequested) {
            System.out.println("[手动压缩]");
            List<MessageParam> compressed = compactor.autoCompact(messageParams);
            messageParams.clear();
            messageParams.addAll(compressed);
            manualCompactRequested = false;
        }
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
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(API_KEY)
            .baseUrl(BASE_URL)
            .build();

        compactor = new ContextCompactor(client, MODEL);
        // 创建技能加载器
        skillLoader = new SkillLoader();
        todoManager = new TodoManager();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(BashDefinition);
        tools.add(ReadFileDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);
        tools.add(SubAgentDefinition);
        tools.add(TodoDefinition);
        tools.add(SkillsDefinition);

        tools.add(ContentCompactDefinition);
        Agent06 agent = new Agent06(client, MODEL, tools, compactor, skillLoader, todoManager);

        // 使用新方法构建系统提示词
        String systemPrompt = generateSystemPrompt(ROOT, skillLoader.getDescriptions());
        agent.setSystemPrompt(systemPrompt);

        System.out.println("=== 上下文压缩智能体 ===");
        System.out.println("Token阈值: " + TOKEN_THRESHOLD);
        System.out.println("保留最近工具结果数: " + KEEP_RECENT);
        System.out.println();

        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
