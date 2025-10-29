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
 * 编辑文件
 * 示例提示词：AI，项目根目录有一个ai_demo.html文件，里面有几个问题，请在里面作答
 * (正确答案是：1、人 ；2、total_tokens = prompt_tokens + completion_tokens = 1802 + 188 = 1990)
 *
 * 示例提示词：AI，在项目根目录下编写一个简单的html，要求里面展示hoppinzq和他的ai网站https://hoppinzq.com/hoppinai/
 * @author hoppinzq
 */
public class EditAgent {

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

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}