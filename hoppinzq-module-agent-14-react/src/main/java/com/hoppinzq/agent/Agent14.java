package com.hoppinzq.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.agent.base.ZQAgent;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.agent.constant.AIConstants.*;
import static com.hoppinzq.agent.tool.ToolDefinition.*;

/**
 * ReAct Agent 示例
 * 使用 ReAct 模式让 AI 能够推理和行动
 *
 * @author hoppinzq
 */
public class Agent14 {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        // 添加基础工具
        tools.add(BashDefinition);
        tools.add(EditFileDefinition);
        tools.add(WriteFileDefinition);
        tools.add(ReadFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(ContentSearchDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);

        // 构建 ReAct 系统提示词或普通系统提示词
        String systemPrompt;
        if (REACT_ENABLE) {
            systemPrompt = buildReActSystemPrompt(tools);
        } else {
            systemPrompt = buildNormalSystemPrompt(tools);
        }

        agent.setSystemPrompt(systemPrompt);
        agent.run();
    }

    /**
     * 构建普通模式系统提示词
     *
     * @param tools 工具列表
     * @return 构建好的提示词字符串
     */
    private static String buildNormalSystemPrompt(List<ToolDefinition> tools) {
        StringBuilder toolDescriptions = new StringBuilder();
        for (ToolDefinition tool : tools) {
            toolDescriptions.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            try {
                toolDescriptions.append("  参数 Schema: ").append(OBJECT_MAPPER.writeValueAsString(tool.getInputSchema())).append("\n");
            } catch (Exception ignored) {}
        }

        return String.format("""
            你是一个专业的编程智能体，具有强大的后台任务执行能力。你可以使用工具来帮助用户解决编程和系统任务。
            
            ## 可用工具
            
            %s
            
            ## 工作目录
            
            你工作在 %s 目录下。
            
            请记住：你的目标是帮助用户高效地完成任务。如果需要使用工具，请直接调用。
            """, toolDescriptions, ROOT);
    }

    /**
     * 构建 ReAct 系统提示词
     *
     * @param tools 工具列表
     * @return 构建好的提示词字符串
     */
    private static String buildReActSystemPrompt(List<ToolDefinition> tools) {
        StringBuilder toolDescriptions = new StringBuilder();
        for (ToolDefinition tool : tools) {
            toolDescriptions.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            try {
                toolDescriptions.append("  参数 Schema: ").append(OBJECT_MAPPER.writeValueAsString(tool.getInputSchema())).append("\n");
            } catch (Exception ignored) {}
        }

        String actionNames = tools.stream().map(ToolDefinition::getName).reduce((a, b) -> a + "," + b).orElse("");
        
        return String.format("""
            你是一个专业的编程智能体，具有强大的后台任务执行能力。你使用 ReAct 模式结合推理和行动来解决复杂问题。
            
            ## ReAct 模式说明
            
            当需要使用工具时，请按照以下格式思考和行动：
            
            Thought: 思考并确定下一步的最佳行动方案
            Action: %s
            Action Input: 工具参数，必须是 JSON 对象
            Observation: 工具执行结果，你不能私自赋值，刚开始没值
            
            ... (Thought/Action/Action Input/Observation 可以重复N次)
            
            ## 重要规则
            
            1. 不使用工具时，回复中不要出现 Thought、Action、Action Input；
            2. 使用工具前，先检查是否缺少必要参数，缺少必要参数时直接向用户提问，不要出现 Thought、Action、Action Input；
            3. 对话时，一次只能返回一个 Thought/Action/Action Input/Observation，绝不能返回多个；
            4. 绝对不能私自给 Observation 赋值，Observation 是 Action 的返回值；
            5. 工具执行遇到问题时，向用户寻求帮助；
            6. 需要执行同一个工具多次时，Action Input 可以出现多次。
            
            ## 示例
            
            用户: 请列出当前目录的文件
            
            AI:
            Thought: 用户想要查看当前目录的文件列表，我应该使用 list_files 工具
            Action: list_files
            Action Input: {}
            
            用户: (工具执行结果) Observation: ["file1.txt", "file2.java", ...]
            
            AI: 我找到了以下文件：file1.txt, file2.java, ...
            
            ## 可用工具
            
            %s
            
            ## 工作目录
            
            你工作在 %s 目录下。
            
            请记住：你的目标是帮助用户高效地完成任务。使用 ReAct 模式，你可以通过思考、行动、观察的循环来逐步解决问题。
            ""","工具名称，必须是[" + actionNames + "]中的一个",toolDescriptions,ROOT);
    }
}
