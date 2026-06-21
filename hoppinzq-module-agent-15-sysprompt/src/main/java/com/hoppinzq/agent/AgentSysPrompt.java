package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.base.ZQAgent;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.tool.prompt.PromptAssembler;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;

/**
 * 动态系统提示词装配示例（对应 Python 教程 s10_system_prompt）。
 *
 * <p>系统提示不再硬编码在 buildSystemPrompt() 里 —— 由 {@link PromptAssembler}
 * 在每轮 LLM 调用前按 {@link com.hoppinzq.agent.tool.prompt.AgentContext} 动态装配：
 * <ul>
 *   <li>identity 段：永远挂</li>
 *   <li>tools 段：按当前 tools 列表渲染</li>
 *   <li>workspace 段：工作目录 + OS</li>
 *   <li>memory 段：仅当 MEMORY.md 存在时挂</li>
 * </ul>
 *
 * <p>context hash 变化才会重新装配（带缓存）。
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
public class  AgentSysPrompt {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ToolDefinition.BashDefinition);
        tools.add(ToolDefinition.ReadFileDefinition);
        tools.add(ToolDefinition.WriteFileDefinition);
        tools.add(ToolDefinition.EditFileDefinition);
        tools.add(ToolDefinition.ListFilesDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        // 不再调 setSystemPrompt —— 完全由 PromptAssembler 装配
        agent.setPromptAssembler(new PromptAssembler());

        System.out.println("\u001b[95m[sysprompt]\u001b[0m 工作目录 " + ROOT);
        System.out.println("提示：手动创建/删除 " + ROOT + "/MEMORY.md，下一轮系统提示会动态切换 memory 段");

        agent.run();
    }
}
