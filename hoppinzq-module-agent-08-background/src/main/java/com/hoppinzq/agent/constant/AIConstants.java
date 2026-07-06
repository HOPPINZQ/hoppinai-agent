package com.hoppinzq.agent.constant;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * @author hoppinzq
 */
public class AIConstants {
    public static final String BASE_URL = "https://api.deepseek.com/anthropic";
    //API KEY 必填
    public static final String API_KEY = System.getenv("DEEPSEEK_API_KEY");
    //模型名称 必填
    public static final String MODEL = "deepseek-v4-flash";

    //最大token数量
    public static final int MAX_TOKENS = 12500;
    public static final long TIMEOUT = 5 * 60;
    public static final int MAX_RETRIES = 5;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String JSON_FAIL = "{\"error\": \"json序列化失败\"}";

    //是否开启打印日志，MCP stdio不允许打印日志，如果你拿去改造为MCP，请注意！
    public static final Boolean LOG_ENABLE = false;

    //路径限制模式：true=允许任意绝对路径（关闭安全沙箱），false=只允许 ROOT 范围内的路径
    //建议本地开发环境设为 true，生产环境设为 false
    public static final Boolean UNRESTRICTED_PATH_MODE = true;

    // ripgrep路径，如果你配置了系统环境变量，将其修改为rg。一般这种集成到应用里的，可以不配置到系统环境变量，直接全路径即可
    // 你可以使用指令`where rg.exe`查看你的rg路径
    public static final String RG_PATH = "C:\\ProgramData\\chocolatey\\bin\\rg.exe";

    public static final String MODULE_NAME = "hoppinzq-module-agent-08-background";
    // 工作目录
    public static final String ROOT = System.getProperty("user.dir") + File.separator + MODULE_NAME;
    // 技能目录
    public static final String SKILL_PATH = "skills";
}
