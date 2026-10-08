package com.hoppinzq.agent.constant;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * s22 多模态常量。
 *
 * @author hoppinzq
 */
public class AIConstants {
    //协议实现：openai（默认）或 anthropic，见 client/LlmProviders
    public static final String PROVIDER = "openai";
    //OpenAI 兼容地址（DeepSeek）或者代理地址 必填；SDK 会在其后拼接 /chat/completions
    public static final String OPENAI_BASE_URL = "https://api.deepseek.com";
    //Anthropic 兼容地址（DeepSeek）或者代理地址 必填；SDK 会在其后拼接 /v1/messages
    public static final String ANTHROPIC_BASE_URL = "https://api.deepseek.com/anthropic";
    public static final String API_KEY = System.getenv("DEEPSEEK_API_KEY");
    public static final String MODEL = "deepseek-v4-flash";

    //采样温度
    public static final double TEMPERATURE = 0.7;

    public static final int MAX_TOKENS = 12500;
    public static final long TIMEOUT = 5 * 60;
    public static final int MAX_RETRIES = 5;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String JSON_FAIL = "{\"error\": \"json序列化失败\"}";

    public static final Boolean LOG_ENABLE = false;

    /**
     * 当前模型是否支持 vision（图片输入）。
     * deepseek-chat 不支持，需设为 false，发送图片时会降级为文本描述。
     */
    public static final boolean SUPPORTS_VISION = false;

    public static final String MODULE_NAME = "hoppinzq-module-agent-22-multimodal";
    public static final String ROOT = System.getProperty("user.dir") + File.separator + MODULE_NAME;
}
