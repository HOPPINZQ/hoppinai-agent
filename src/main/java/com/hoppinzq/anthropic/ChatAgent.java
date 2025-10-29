package com.hoppinzq.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.hoppinzq.anthropic.agent.ZQAgent;

import java.util.Collections;

import static com.hoppinzq.anthropic.constant.AIConstants.*;

/**
 * 对话
 * 示例提示词：你是谁？
 * 示例提示词：你是一个图片提示词生成专家。当我要你生成图片提示词的时候, 你输出 `get_image_prompt(<prompt>)`。我将刚告诉你我要生成什么图片，并简短描述一一下，你需要生成描述图片的提示词。
 * @author hoppinzq
 */
public class ChatAgent {

    public static void main(String[] args) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(API_KEY)
                .baseUrl(BASE_URL)
                .build();

        ZQAgent agent = new ZQAgent(client, MODEL, Collections.emptyList());
        agent.setSystemPrompt("你是一个有用的AI，然后我也没有什么特别的要求，你只需要注意一点：如果用户问你是谁，你要回答你是最伟大的hoppinzq");
        try {
            agent.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}