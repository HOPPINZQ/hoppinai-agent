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
 * 示例提示词：AI，打开百度并搜索hoppinzq，然后再访问hoppinzq.com
 * 示例提示词：AI，查看我电脑里的进程，并关闭DOTA2的游戏进程，因为它卡死了
 * @author hoppinzq
 */
public class BashAgent {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(ReadFileDefinition);
        tools.add(ListFilesDefinition);
        tools.add(BashDefinition);

        ZQAgent agent = new ZQAgent(client, MODEL, tools);
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}