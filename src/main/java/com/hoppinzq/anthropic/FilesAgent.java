package com.hoppinzq.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.anthropic.agent.ZQAgent;
import com.hoppinzq.anthropic.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.anthropic.constant.AIConstants.*;
import static com.hoppinzq.anthropic.tool.ToolDefinition.ListFilesDefinition;
import static com.hoppinzq.anthropic.tool.ToolDefinition.ReadFileDefinition;

/**
 * 列出文件
 * 示例提示词：AI，这个工程有多少文件，ai_demo.html在哪？
 * @author hoppinzq
 */
public class FilesAgent {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ReadFileDefinition);
        tools.add(ListFilesDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}