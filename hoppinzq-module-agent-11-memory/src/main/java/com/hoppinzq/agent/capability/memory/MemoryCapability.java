package com.hoppinzq.agent.capability.memory;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentCommand;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;

import static com.hoppinzq.agent.constant.AIConstants.ROOT;

import java.util.List;

/**
 * 跨会话持久记忆能力（对应 Python 教程 s09_memory）。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>1 个钩子：{@code beforeLlmCall} —— 注入与记忆抽取共用（均为只读，不改消息列表）
 *       <ul>
 *         <li>注入：按最新用户输入挑选相关记忆，动态刷新系统提示的"相关记忆"段</li>
 *         <li>抽取：检测到上一轮已以 assistant 文本收尾时，调 LLM 抽取新事实写入 .memory/</li>
 *       </ul></li>
 *   <li>1 个命令：{@code /memory}（查看记忆索引与统计）</li>
 *   <li>系统提示片段：告知模型记忆机制的存在与配合方式</li>
 * </ul>
 *
 * <p>为什么不用 {@code promptFragments} 做注入：片段在装配期拼接一次，而相关记忆
 * 依赖"每轮的最新用户输入"，只能逐轮刷新；故片段只放静态说明，动态部分走钩子。
 *
 * <p>拔除方式：删除本目录 + 入口类的注册行即可，内核不感知本能力。
 *
 * @author hoppinzq
 */
public class MemoryCapability implements Capability {

    private final MemoryStore store;
    private final MemorySelector selector;
    private final MemoryExtractor extractor;

    /** onStart 捕获的宿主 Agent：读消息列表、刷新系统提示 */
    private ZQAgent agent;
    /** 装配期的基础系统提示（不含记忆段）；首次 LLM 调用时从宿主捕获 */
    private String baseSystemPrompt;
    /** 上次注入时依据的用户输入：同一输入不重复调选择器（省一次 LLM 调用） */
    private String lastInjectedPrompt;
    /** 抽取水位：记录已抽取时的消息条数，避免同一轮被重复抽取 */
    private int extractedUpTo = -1;

    public MemoryCapability(LlmProvider provider) {
        this.store = new MemoryStore(ROOT, provider);
        this.selector = new MemorySelector(store);
        this.extractor = new MemoryExtractor(store);
    }

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
    }

    // ============================== 钩子 ==============================

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
                // 只读消息列表，原样返回：记忆机制不改变对话上下文本身
                captureBasePromptIfNeeded();
                extractFromFinishedTurn(messages);
                injectRelevantMemories(messages);
                return messages;
            }
        });
    }

    /** 首次调用时捕获装配期的基础提示，后续在其后拼接"相关记忆"段 */
    private void captureBasePromptIfNeeded() {
        if (baseSystemPrompt == null && agent != null) {
            baseSystemPrompt = agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt();
        }
    }

    /**
     * 记忆抽取：上一轮已结束（最后一条是 assistant）时，从"最近一条用户输入 + 最终回复"中
     * 抽取值得长期记住的事实并落盘。工具循环中间态（最后一条是 tool）不触发。
     */
    private void extractFromFinishedTurn(List<LlmMessage> messages) {
        if (!MemoryConfig.EXTRACT_ENABLED || messages.size() < 2 || messages.size() <= extractedUpTo) {
            return;
        }
        LlmMessage last = messages.get(messages.size() - 1);
        if (!last.isAssistant() || last.getText() == null || last.getText().isBlank()) {
            return;
        }
        String userPrompt = findLastUserText(messages);
        if (userPrompt == null) {
            return;
        }
        extractedUpTo = messages.size();
        try {
            extractor.extractAndSave(userPrompt, last.getText());
        } catch (Exception e) {
            // 记忆是锦上添花：抽取失败不阻断主对话
            System.out.println("[memory] 抽取失败: " + e.getMessage());
        }
    }

    /**
     * 记忆注入：按最新用户输入选相关记忆，刷新系统提示。
     * <p>内核的 chatMessage 在分发钩子之后才读取 systemPrompt 字段，
     * 因此钩子内刷新对本轮调用即刻生效。
     */
    private void injectRelevantMemories(List<LlmMessage> messages) {
        if (agent == null || baseSystemPrompt == null) {
            return;
        }
        String userPrompt = findLastUserText(messages);
        if (userPrompt == null || userPrompt.equals(lastInjectedPrompt)) {
            return;
        }
        lastInjectedPrompt = userPrompt;
        try {
            String relevant = selector.selectAndFormat(userPrompt);
            if (relevant == null || relevant.isBlank()) {
                agent.setSystemPrompt(baseSystemPrompt);
            } else {
                agent.setSystemPrompt(baseSystemPrompt
                        + "\n\n## 相关记忆（来自 .memory/，与当前问题相关）\n" + relevant.trim());
            }
        } catch (Exception e) {
            System.out.println("[memory] 注入失败: " + e.getMessage());
        }
    }

    /** 从消息列表尾部向前找最近一条用户输入文本 */
    private String findLastUserText(List<LlmMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage m = messages.get(i);
            if (m.isUser() && m.getText() != null && !m.getText().isBlank()) {
                return m.getText();
            }
        }
        return null;
    }

    // ============================== 命令 ==============================

    @Override
    public List<AgentCommand> commands() {
        return List.of(new AgentCommand() {
            @Override
            public String name() {
                return "/memory";
            }

            @Override
            public String description() {
                return "查看持久记忆索引与统计";
            }

            @Override
            public void execute() {
                System.out.println("========== 持久记忆 ==========");
                System.out.println("记忆条数: " + store.fileCount()
                        + "（阈值 " + MemoryConfig.CONSOLIDATE_THRESHOLD + " 后建议 consolidate）");
                String index = store.readIndex();
                if (index.isBlank()) {
                    System.out.println("（索引为空）");
                } else {
                    System.out.println(index.trim());
                }
            }
        });
    }

    // ============================== 提示词与描述 ==============================

    @Override
    public List<String> promptFragments() {
        return List.of(
                "- 系统具备跨会话持久记忆：每轮自动挑选相关记忆注入系统提示，并在对话结束后抽取新事实写入 .memory/ 目录\n" +
                "- 当用户告知偏好、纠正你的做法或描述项目约定时，请用陈述句表达确认，便于记忆抽取器落盘");
    }

    /** 启动横幅用的能力摘要 */
    public String description() {
        return "跨会话持久记忆：\n" +
                "  存储: " + MemoryConfig.MEMORY_DIR + "/ + 索引 " + MemoryConfig.MEMORY_INDEX_FILE + "\n" +
                "  注入: 每轮按最新输入选相关记忆（最多 " + MemoryConfig.MAX_INJECT + " 条）\n" +
                "  抽取: 每轮结束后从对话中提取新事实（" + (MemoryConfig.EXTRACT_ENABLED ? "开启" : "关闭") + "）";
    }

    /** 当前已落盘的记忆条数（供入口类启动横幅使用） */
    public int fileCount() {
        return store.fileCount();
    }
}
