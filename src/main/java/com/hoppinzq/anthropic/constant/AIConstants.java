package com.hoppinzq.anthropic.constant;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * 一些常量
 * @author hoppinzq
 */
public class AIConstants {
    //anthropic 地址 或者代理地址 必填
    public static final String BASE_URL = "https://open.bigmodel.cn/api/anthropic";
    //API KEY 必填
    public static final String API_KEY = "智谱APIKEY";
    //模型名称 必填
    public static final String MODEL = "glm-4.5";
    //最大token数量
    public static final int MAX_TOKENS = 4096;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String JSON_FAIL = "{\"error\": \"json序列化失败\"}";

    //是否开启打印日志，MCP stdio不允许打印日志，如果你拿去改造为MCP，请注意！
    public static final Boolean LOG_ENABLE = true;

    // ripgrep路径，如果你配置了系统环境变量，将其修改为rg。一般这种集成到应用里的，可以不配置到系统环境变量，直接全路径即可
    // 你可以使用指令`where rg.exe`查看你的rg路径
    public static final String RG_PATH = "C:\\ProgramData\\chocolatey\\bin\\rg.exe";

    // 联网搜索调用的百度接口，文档：https://cloud.baidu.com/product/ai-search.html
    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder().readTimeout(10, TimeUnit.SECONDS).build();
    public static final String WEB_SEARCH_KEY = "百度联网搜索key";
}
