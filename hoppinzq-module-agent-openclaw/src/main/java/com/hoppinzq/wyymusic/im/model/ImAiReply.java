package com.hoppinzq.wyymusic.im.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 给私信会话生成的回复（文本 + 可选的点歌信息）。
 *
 * @author hoppinzq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImAiReply {

    /** 回复正文 */
    private String reply;
    /** 歌曲搜索关键词；为空表示不推荐歌曲 */
    private String songQuery;
    /** 一句话推荐语（随歌曲卡片一起发送） */
    private String songReason;
}