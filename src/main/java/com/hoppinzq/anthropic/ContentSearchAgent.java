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
 * 搜索代码和内容，基于ripgrep
 * 示例提示词：AI，查找所有的html文件，回答所有的，文件里包含"这里有一个问题"的答案
 * @author hoppinzq
 */
public class ContentSearchAgent {

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
        tools.add(ContentSearchDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}