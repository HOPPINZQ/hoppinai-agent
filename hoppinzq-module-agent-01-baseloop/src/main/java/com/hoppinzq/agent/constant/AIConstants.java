package com.hoppinzq.agent.constant;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * 一些常量
 *
 * @author hoppinzq
 */
public class AIConstants {
    //anthropic 地址 或者代理地址 必填
    public static final String BASE_URL = "https://api.deepseek.com/anthropic";
    //API KEY 必填
    public static final String API_KEY = System.getenv("DEEPSEEK_API_KEY");
    //模型名称 必填
    public static final String MODEL = "deepseek-v4-flash";

    public static final long TIMEOUT = 5 * 60;
    public static final int MAX_RETRIES = 5;

    //最大token数量
    public static final int MAX_TOKENS = 12500;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String JSON_FAIL = "{\"error\": \"json序列化失败\"}";

    //是否开启打印日志，MCP stdio不允许打印日志，如果你拿去改造为MCP，请注意！
    public static final Boolean LOG_ENABLE = false;

    public static final String MODULE_NAME = "hoppinzq-module-agent-01-baseloop";
    // 工作目录
    public static final String ROOT = System.getProperty("user.dir") + File.separator + MODULE_NAME;
}
