package com.hoppinzq.agent.capability.prompt;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.AgentHook;
import com.hoppinzq.agent.capability.Capability;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.constant.AIConstants;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 动态系统提示词装配能力（可整体复制的能力包）。
 *
 * <p>系统提示不再写死：每轮 LLM 调用前由 {@link PromptAssembler} 按
 * {@link AgentContext} 条件渲染 section，再回写到宿主 Agent 的 systemPrompt。
 * 由于内核 {@code chatMessage} 的执行顺序是「先分发给钩子、再读取 systemPrompt 构造请求」，
 * 钩子里改写 systemPrompt 对本轮请求立即生效 —— 因此<b>不需要扩展内核契约</b>。
 *
 * <p>向内核提供：
 * <ul>
 *   <li>1 个钩子：{@code beforeLlmCall} 里重新装配并改写 systemPrompt（context-hash 缓存，context 不变则不重算）</li>
 *   <li>4 个 section（由 {@link PromptAssembler} 注册）：identity / tools / workspace / memory</li>
 * </ul>
 *
 * <p>memory 段是"活"的：{@code <ROOT>/MEMORY.md} 存在就挂上、删掉就撤下，正好演示动态装配。
 *
 * <p>对内核的依赖仅限：Capability 契约、ZQAgent（onStart 捕获宿主）、AIConstants.ROOT。
 * 拔除本目录并删除注册行即可完全移除该能力。
 *
 * @author hoppinzq
 */
public class PromptCapability implements Capability {

    /** 提示词装配器（section 注册与 context-hash 缓存都在内部） */
    private final PromptAssembler assembler;

    /** onStart 捕获的宿主 Agent：钩子里要读它的工具表、改写它的 systemPrompt */
    private ZQAgent agent;

    public PromptCapability() {
        this(new PromptAssembler());
    }

    public PromptCapability(PromptAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    public String name() {
        return "prompt";
    }

    @Override
    public void onStart(ZQAgent agent) {
        this.agent = agent;
    }

    @Override
    public List<AgentHook> hooks() {
        return List.of(new AgentHook() {
            @Override
            public List<LlmMessage> beforeLlmCall(List<LlmMessage> messages) {
                // 上下文列表本身不动，只按当前环境重算系统提示
                agent.setSystemPrompt(assembler.get(buildContext()));
                return messages;
            }
        });
    }

    /**
     * 按宿主 Agent 的当前状态构造装配上下文。
     * <p>toolNames 取自 Agent 实际注册的工具（含各能力贡献的工具），
     * memoryEnabled 每次实时探测 MEMORY.md 是否存在。
     */
    private AgentContext buildContext() {
        Set<String> names = new HashSet<>();
        if (agent.getTools() != null) {
            for (var tool : agent.getTools()) {
                names.add(tool.getName());
            }
        }
        return AgentContext.builder()
                .workspace(AIConstants.ROOT)
                .osName(System.getProperty("os.name").toLowerCase())
                .toolNames(List.copyOf(names))
                .memoryEnabled(Files.isRegularFile(Paths.get(AIConstants.ROOT, "MEMORY.md")))
                .build();
    }

    /** 启动横幅描述 */
    public String description() {
        return "系统提示由 PromptAssembler 动态装配：identity / tools / workspace / memory 四段，"
                + "MEMORY.md 存在与否实时决定 memory 段开关";
    }
}