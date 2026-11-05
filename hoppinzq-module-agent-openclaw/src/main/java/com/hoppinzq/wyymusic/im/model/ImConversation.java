package com.hoppinzq.wyymusic.im.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网易云私信会话（一个对话人 + 最后一条消息）。
 *
 * @author hoppinzq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImConversation {

    /** 对话对方用户 id */
    private long userId;
    /** 对方昵称 */
    private String nickname;
    /** 对方头像 */
    private String avatarUrl;
    /** 最后一条消息的文本摘要 */
    private String lastMessage;
    /** 最后一条消息时间（毫秒时间戳） */
    private long lastTime;
    /** 未读消息数（接口不返回时为 0） */
    private int newMsgCount;
}