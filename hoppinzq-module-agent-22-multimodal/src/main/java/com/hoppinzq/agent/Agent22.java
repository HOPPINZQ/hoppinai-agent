package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.multimodal.MultimodalCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.command.AgentCommandHandler;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.BashDefinition;

/**
 * s22 多模态输入智能体。
 *
 * <p>本类只负责<b>装配</b>：多模态工具（read_file 图片增强 / screenshot）、/img 命令、
 * 提示词片段全部收拢在 {@link MultimodalCapability}（capability.multimodal 包），
 * 入口类不再承载任何多模态逻辑。
 *
 * <p>这就是插拔式集成的完整形态：复制 {@code capability/multimodal/} 目录 +
 * 下面 main 中的一行 {@code agent.capability(new MultimodalCapability())}。
 *
 * <p>示例提示词：
 * <ul>
 *   <li>"读取 test.png"（工作目录放一张图片，会自动 base64）</li>
 *   <li>"截个屏"（调用 screenshot 工具）</li>
 *   <li>"/img test.png 这张图里有什么？"（直接附图给 AI）</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class Agent22 extends ZQAgent {

    /** 多模态能力：/img 前缀命令需要在其上解析参数，保存为字段供命令分发覆写使用 */
    private final MultimodalCapability multimodal;

    public Agent22(LlmProvider provider, String model, List<ToolDefinition> tools, MultimodalCapability multimodal) {
        super(provider, model, tools);
        this.multimodal = multimodal;
    }

    /**
     * 基础系统提示词（不含能力片段；能力通过 promptFragments 贡献自己的部分）。
     */
    public static String generateSystemPrompt(String root) {
        return String.format("""
                你是一个多模态 AI 编程助手，由 hoppinzq 创建。

                ## 核心能力

                - bash: 执行 Shell 命令
                - read_file: 读取文件；遇到图片会自动转 base64 image block
                - screenshot: 抓取当前屏幕

                ## 工作原则

                1. 优先使用工具完成任务。
                2. 用户给出图片时，先描述图片内容再回答问题。

                ## 身份

                你是由最伟大的 hoppinzq 创建的 AI 助手，工作在目录: %s。""", root);
    }

    /**
     * 命令分发：/img 带参数时无法被内核的"命令名精确匹配"命中（{@code /img test.png} ≠ {@code /img}），
     * 因此在命令处理器外包一层，先按前缀转交能力解析，其余命令回落到内核处理器。
     */
    @Override
    protected AgentCommandHandler createCommandHandler(SessionManager sessionManager, SkillLoader skillLoader) {
        AgentCommandHandler base = super.createCommandHandler(sessionManager, skillLoader);
        return new AgentCommandHandler(sessionManager, skillLoader, multimodal.commands()) {
            @Override
            public boolean handleCommand(String input) {
                if (multimodal.handleImgCommand(input)) {
                    return true;
                }
                return super.handleCommand(input);
            }
        };
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();

        // 会话启动交互已收拢到 SessionManager.bootstrap
        SessionManager sessionManager = SessionManager.bootstrap(args);

        // 基座工具（不含任何能力工具）
        List<ToolDefinition> baseTools = new ArrayList<>(List.of(BashDefinition));

        // 插拔式集成：复制 capability/multimodal/ 目录 + 这一行注册
        MultimodalCapability multimodal = new MultimodalCapability();
        Agent22 agent = new Agent22(provider, MODEL, baseTools, multimodal);
        agent.capability(multimodal);

        // 基础提示 + 能力贡献的提示片段
        agent.setSystemPrompt(generateSystemPrompt(ROOT)
                + "\n\n" + String.join("\n", multimodal.promptFragments()));

        System.out.println("=== s22 多模态智能体 ===");
        System.out.println(multimodal.description());
        System.out.println();

        // 本模块无技能体系，SkillLoader 传 null（/skills 命令会提示未启用）
        agent.setSessionManager(sessionManager, null);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
