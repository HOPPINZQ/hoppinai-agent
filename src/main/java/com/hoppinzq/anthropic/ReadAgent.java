package com.hoppinzq.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.anthropic.agent.ZQAgent;
import com.hoppinzq.anthropic.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.anthropic.constant.AIConstants.*;
import static com.hoppinzq.anthropic.tool.ToolDefinition.ReadFileDefinition;

/**
 * 读取文件内容
 * 示例提示词：AI，ai_demo.html里面有几个问题，答案是什么？
 * (正确答案是：1、人 ；2、total_tokens = prompt_tokens + completion_tokens = 1802 + 188 = 1990)
 * @author hoppinzq
 */
public class ReadAgent {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ReadFileDefinition);
        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}