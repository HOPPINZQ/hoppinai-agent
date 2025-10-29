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
 * zq智能体
 * @author hoppinzq
 */
public class ZQAllAgent {
    
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
        tools.add(ContentSearchDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        agent.setSystemPrompt("你是一个有用的AI，然后我也没有什么特别的要求，你只需要注意一点：如果用户问你是谁，你要回答你是最伟大的hoppinzq");
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
