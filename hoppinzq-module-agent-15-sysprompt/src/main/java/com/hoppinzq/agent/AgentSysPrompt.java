package com.hoppinzq.agent;

import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.capability.prompt.PromptCapability;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.session.SessionManager;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.skill.SkillLoader;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.MODEL;
import static com.hoppinzq.agent.constant.AIConstants.ROOT;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * 动态系统提示词装配示例（对应 Python 教程 s10_system_prompt）。
 *
 * <p>本类只负责<b>装配</b>：系统提示的装配逻辑收拢在 {@link PromptCapability}
 * （capability.prompt 包），它通过 {@code beforeLlmCall} 钩子在每轮请求前
 * 重新装配并改写 systemPrompt —— 入口类不再需要 {@code setPromptAssembler} 之类的内核扩展点。
 *
 * <p>4 个 section（按注册顺序渲染）：
 * <ul>
 *   <li>identity 段：永远挂</li>
 *   <li>tools 段：按当前 tools 列表渲染</li>
 *   <li>workspace 段：工作目录 + OS</li>
 *   <li>memory 段：仅当 MEMORY.md 存在时挂</li>
 * </ul>
 *
 * <p><b>示例 prompt：</b>
 * <ul>
 *   <li>启动时若 MEMORY.md 不存在 → system prompt 不含 memory 段</li>
 *   <li>在工作目录手动创建 MEMORY.md → 下一轮 system prompt 自动加上 memory 段</li>
 *   <li>删掉 MEMORY.md → 下一轮 system prompt 又自动撤掉 memory 段</li>
 * </ul>
 *
 * @author hoppinzq
 */
public class AgentSysPrompt extends ZQAgent {

    /** 命令处理器需要技能加载器（/skills），须先赋值 */
    public static SkillLoader skillLoader;

    public AgentSysPrompt(LlmProvider provider, String model, List<ToolDefinition> tools) {
        super(provider, model, tools);
    }

    public static void main(String[] args) {
        LlmProvider provider = LlmProviders.create();
        skillLoader = new SkillLoader();

        List<ToolDefinition> baseTools = new ArrayList<>(List.of(
                BashDefinition, ReadFileDefinition, WriteFileDefinition,
                EditFileDefinition, ListFilesDefinition));

        AgentSysPrompt agent = new AgentSysPrompt(provider, MODEL, baseTools);

        // 插拔式集成：复制 capability/prompt/ 目录 + 这一行注册
        // 不再调 setSystemPrompt —— 完全由能力的 beforeLlmCall 钩子装配
        PromptCapability prompt = new PromptCapability();
        agent.capability(prompt);

        System.out.println("=== 动态系统提示词智能体 ===");
        System.out.println(prompt.description());
        System.out.println("\u001b[95m[sysprompt]\u001b[0m 工作目录 " + ROOT);
        System.out.println("提示：手动创建/删除 " + ROOT + "/MEMORY.md，下一轮系统提示会动态切换 memory 段");
        System.out.println();

        SessionManager sessionManager = SessionManager.bootstrap(args);
        agent.setSessionManager(sessionManager, skillLoader);
        agent.run();
    }
}
