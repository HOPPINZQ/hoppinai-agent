package com.hoppinzq.agent.constant;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * 一些常量
 * @author hoppinzq
 */
public class AIConstants {
    //协议实现：openai（默认）或 anthropic，见 client/LlmProviders
    public static final String PROVIDER = "openai";
    //OpenAI 兼容地址（DeepSeek）或者代理地址 必填；SDK 会在其后拼接 /chat/completions
    public static final String OPENAI_BASE_URL = "https://api.deepseek.com";
    //Anthropic 兼容地址（DeepSeek）或者代理地址 必填；SDK 会在其后拼接 /v1/messages
    public static final String ANTHROPIC_BASE_URL = "https://api.deepseek.com/anthropic";
    //API KEY 必填（两个端点通用）
    public static final String API_KEY = System.getenv("DEEPSEEK_API_KEY");
    //模型名称 必填
    public static final String MODEL = "deepseek-v4-flash";

    //最大token数量
    public static final int MAX_TOKENS = 12500;
    //采样温度（teammate 循环等使用）
    public static final double TEMPERATURE = 0.5;
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

    public static final String MODULE_NAME = "hoppinzq-module-agent-comprehensive";
    // 工作目录
    public static final String ROOT = System.getProperty("user.dir") + File.separator + MODULE_NAME;
    public static final String WORKTREES_DIR_NAME = ".worktrees";
    public static final String WORKTREES_DIR = ROOT + File.separator + WORKTREES_DIR_NAME;

    // skills 目录（SkillLoader 从 classpath 加载）
    public static final String SKILL_PATH = "skills";
    // 上下文压缩阈值
    public static final int TOKEN_THRESHOLD = 20000;
    public static final String TRANSCRIPT_DIR = ROOT + File.separator + ".transcripts";
    public static final int KEEP_RECENT = 10;

    // ========================= ContextCompactor 压缩策略常量 =========================
    // L1 snip_compact：消息数超过该值时裁掉中段
    public static final int MAX_MESSAGES = 50;
    // L1 snip_compact：头部保留的消息条数
    public static final int KEEP_HEAD = 3;
    // L3 tool_result_budget：单条消息总字节数超过该值时持久化大输出
    public static final int MAX_BYTES_PER_MESSAGE = 200_000;
    // L3 tool_result_budget：单个工具结果超过该字节数才持久化到磁盘
    public static final int PERSIST_THRESHOLD = 30000;
}
