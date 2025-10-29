package com.hoppinzq.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.anthropic.agent.ZQAgent;
import com.hoppinzq.anthropic.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static com.hoppinzq.anthropic.constant.AIConstants.*;
import static com.hoppinzq.anthropic.tool.ToolDefinition.*;

/**
 * 使用命令行
 * 示例提示词：AI，最近有一个很火的'hoppinzq'，你知道吗？在项目根目录下编写一个简单的html来介绍他
 * @author hoppinzq
 */
public class WebSearchAgent {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ReadFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(BashDefinition);
        tools.add(EditFileDefinition);
        tools.add(WebSearchDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}