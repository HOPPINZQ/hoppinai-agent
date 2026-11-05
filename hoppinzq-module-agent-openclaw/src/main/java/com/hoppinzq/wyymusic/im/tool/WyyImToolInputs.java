package com.hoppinzq.wyymusic.im.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 私信相关 agent 工具入参。
 *
 * @author hoppinzq
 */
public final class WyyImToolInputs {

    private WyyImToolInputs() {
    }

    /** 无参数工具 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Empty {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Conversations {
        /** 最多返回多少个会话 */
        private Integer limit;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class History {
        /** 对方用户 id */
        private Long uid;
        /** 最多返回多少条消息 */
        private Integer limit;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchSong {
        /** 搜索关键词，如「周杰伦 晴天」 */
        private String keywords;
        /** 返回数量 */
        private Integer limit;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SendText {
        /** 对方用户 id */
        private Long uid;
        /** 私信内容 */
        private String msg;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SendSong {
        /** 对方用户 id */
        private Long uid;
        /** 歌曲 id，与 keywords 二选一；优先使用 */
        private Long songId;
        /** 搜歌关键词，songId 为空时使用 */
        private String keywords;
        /** 随歌曲卡片发送的一句话 */
        private String msg;
    }
}