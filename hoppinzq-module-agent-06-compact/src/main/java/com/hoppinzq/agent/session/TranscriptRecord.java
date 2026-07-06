package com.hoppinzq.agent.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 压缩记录：记录一次压缩操作的详细信息。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptRecord {
    /**
     * 压缩时间戳
     */
    private String timestamp;

    /**
     * 压缩前的消息数量
     */
    private int messageCount;

    /**
     * 压缩前的估算 token 数量
     */
    private int estimatedTokens;

    /**
     * 压缩摘要
     */
    private String summary;

    /**
     * 压缩原因（auto/manual）
     */
    private String reason;
}
