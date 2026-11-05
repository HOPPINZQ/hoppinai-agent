package com.hoppinzq.wyymusic.im;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网易云私信 AI 机器人的配置。
 *
 * <pre>
 * wyy:
 *   im:
 *     enabled: true
 *     cookie: ${WYY_COOKIE:}
 * </pre>
 *
 * @author hoppinzq
 */
@Data
@Component
@ConfigurationProperties(prefix = "wyy.im")
public class WyyImProperties {

    /** 是否开启私信自动回复 */
    private boolean enabled = true;

    /** NeteaseCloudMusicApi 服务地址 */
    private String baseUrl = "https://hoppinzq.com:3003/";

    /** 网易云登录 cookie；留空则整个功能不会执行 */
    private String cookie = "";

    /** 轮询间隔（毫秒） */
    private long pollIntervalMs = 60_000;

    /** 启动后首次轮询延迟（毫秒） */
    private long initialDelayMs = 15_000;

    /** 单次最多处理的会话数 */
    private int maxConversations = 20;

    /** 每个会话最多拉取多少条历史消息 */
    private int maxHistory = 20;

    /** 只处理最近多少分钟内的消息；<=0 表示不限制 */
    private int maxAgeMinutes = 180;

    /** 演练模式：调用 AI、打印计划，但不真正发送私信 */
    private boolean dryRun = false;

    /** 是否允许推荐歌曲 */
    private boolean sendSong = true;

    /** 回复正文最大长度（字符） */
    private int maxReplyLength = 300;

    /** HTTP 超时（秒） */
    private long requestTimeoutSeconds = 20;

    /** 视为机器人的用户 id（默认跳过网易云小秘书） */
    private List<Long> botUserIds = new ArrayList<>(List.of(9003L));

    /** 昵称命中以下关键词的会话视为机器人 */
    private List<String> botNicknameKeywords = new ArrayList<>(
            List.of("小秘书", "机器人", "客服", "官方", "助手", "bot"));

    /** AI 系统提示词；留空使用内置默认值 */
    private String systemPrompt = "";

    public String effectiveSystemPrompt() {
        return (systemPrompt == null || systemPrompt.isBlank()) ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
    }

    public static final String DEFAULT_SYSTEM_PROMPT = """
            你叫「云村小助手」，是网易云音乐私信里一个有人情味的音乐搭子。
            你的任务：根据最近的私信对话，自然地回复对方，并在合适的时候分享一首网易云上的歌。

            输出要求：只输出一个 JSON 对象，不要输出 Markdown 代码块或任何额外解释。
            JSON 格式：
            {"reply":"回复正文","songQuery":"搜索歌曲的关键词，如「周杰伦 晴天」或「适合雨天的民谣」；不推荐歌曲时填 null","songReason":"一句话推荐语；不推荐歌曲时填 null"}

            规则：
            1. reply 用中文口语，1~3 句话，尽量控制在 80 字以内；不要自称 AI，不要说“作为一个人工智能”。
            2. 对方聊音乐、点歌、说心情、求推荐，或对话氛围适合来一首歌时，给出 songQuery；否则 songQuery 填 null，不要强行推歌。
            3. songQuery 尽量包含“歌名 歌手”；不确定歌名时，用风格或场景关键词。
            4. 不要编造歌曲 id、播放链接或歌手信息。
            5. 对方道谢、告别时，简短回应即可。
            6. 不讨论敏感内容，不引导转账、付费或点击外链。
            """;
}