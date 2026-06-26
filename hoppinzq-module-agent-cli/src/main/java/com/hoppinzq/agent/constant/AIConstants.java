package com.hoppinzq.agent.constant;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * CLI 模块常量。
 *
 * @author hoppinzq
 */
public class AIConstants {
    // anthropic 地址 或者代理地址 必填
    public static final String BASE_URL = "https://api.deepseek.com/anthropic";
    // API KEY 必填
    public static final String API_KEY = "sk-xxxxxxxxxxxxxxxxxxxxxxx";
    // 模型名称 必填
    public static final String MODEL = "deepseek-chat";

    // 最大 token 数量
    public static final int MAX_TOKENS = 25000;
    public static final double TEMPERATURE = 0.7D;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String JSON_FAIL = "{\"error\": \"json序列化失败\"}";

    // 是否开启打印日志（CLI 中默认开启由 CliRenderer 接管，此开关仅控制 SLF4J log）
    public static final Boolean LOG_ENABLE = false;

    // ripgrep 路径
    public static final String RG_PATH = "C:\\ProgramData\\chocolatey\\bin\\rg.exe";

    // 是否开启 ReAct 模式
    public static final Boolean REACT_ENABLE = true;

    public static final String MODULE_NAME = "hoppinzq-module-agent-cli";
    // 工作目录
    public static final String ROOT = System.getProperty("user.dir") + File.separator + MODULE_NAME;
    public static final String TRANSCRIPT_DIR = ROOT + File.separator + ".transcripts";
    // 技能目录
    public static final String SKILL_PATH = "skills";
    // 压缩配置
    public static final int TOKEN_THRESHOLD = 20000;
    public static final int KEEP_RECENT = 10;
}
