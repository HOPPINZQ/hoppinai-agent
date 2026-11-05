package com.hoppinzq.wyymusic.im.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条网易云私信。
 *
 * @author hoppinzq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImMessage {

    /** 消息 id；接口不返回时为 0 */
    private long msgId;
    /** 是否由当前登录账号发出 */
    private boolean fromSelf;
    /** 发送者用户 id */
    private long senderId;
    /** 发送者昵称 */
    private String senderName;
    /** 消息正文；非文本消息为可读的占位描述 */
    private String text;
    /** 消息时间（毫秒时间戳） */
    private long time;
    /** 消息类型（接口原始 type 字段） */
    private int type;
}