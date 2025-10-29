package com.hoppinzq.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.hoppinzq.anthropic.agent.ZQAgent;
import com.hoppinzq.anthropic.tool.ToolDefinition;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hoppinzq.anthropic.constant.AIConstants.*;
import static com.hoppinzq.anthropic.tool.ToolDefinition.ReadFileDefinition;

/**
 * 对话
 * 示例提示词：AI，ai_demo.html里面有几个问题，答案是什么？
 *
 * @author hoppinzq
 */
public class ChatReActAgent {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ReadFileDefinition);
        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        try {
            String USER_PROMPT = "你是一位经验丰富AI智能助理，专注于文件检索和编辑，你现在要三思而后行";
            agent.setSystemPrompt(prompt(USER_PROMPT, tools));
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String prompt(String userPrompt, List<ToolDefinition> tools) throws JsonProcessingException {
        String prompt = "${{user_prompt}}\n"
                + "---------------------\n"
                + "# 工具列表\n"
                + "${{tool_definitions}}\n"
                + "\n"
                + "使用如下格式：\n"
                + "Thought: 思考并确定下一步的最佳行动方案\n"
                + "Action: ${{agent_action}}\n"
                + "Action Input: 工具参数，必须是 JSON 对象\n"
                + "Observation: 工具执行结果\n"
                + "... (Thought/Action/Action Input/Observation 可以重复N次)\n"
                + "\n"
                + "注意：\n"
                + "- 不使用工具时，回复中不要出现 Thought、Action、Action Input；\n"
                + "- 使用工具前，先检查是否缺少必要参数，缺少必要参数时直接向用户提问，不要出现 Thought、Action、Action Input；\n"
                + "- 永远不要对Observation私自赋值，Observation是工具调用的结果；\n"
                + "- 工具执行遇到问题时，向用户寻求帮助；\n"
                + "- 需要执行同一个工具多次时，Action Input 可以出现多次；\n";
        prompt = prompt.replace("${{user_prompt}}", userPrompt);
        prompt = prompt.replace("${{tool_definitions}}", OBJECT_MAPPER.writeValueAsString(tools));
        prompt = prompt.replace("${{agent_action}}", MessageFormat.format("工具名称，必须是[{0}]中的一个", tools.stream().map(ToolDefinition::getName).collect(Collectors.joining(","))));
        return prompt;
    }
}