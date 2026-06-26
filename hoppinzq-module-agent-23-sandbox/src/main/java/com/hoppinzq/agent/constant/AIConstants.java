package com.hoppinzq.agent.constant;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * s23 沙箱模块常量。
 *
 * @author hoppinzq
 */
public class AIConstants {
    public static final String BASE_URL = "https://api.deepseek.com/anthropic";
    public static final String API_KEY = "sk-xxxxxxxxxxxxxxxxxxxxxxx";
    public static final String MODEL = "deepseek-chat";

    public static final int MAX_TOKENS = 12500;
    public static final double TEMPERATURE = 0.7D;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String JSON_FAIL = "{\"error\": \"json序列化失败\"}";

    public static final Boolean LOG_ENABLE = false;

    public static final String MODULE_NAME = "hoppinzq-module-agent-23-sandbox";
    public static final String ROOT = System.getProperty("user.dir") + File.separator + MODULE_NAME;
}
