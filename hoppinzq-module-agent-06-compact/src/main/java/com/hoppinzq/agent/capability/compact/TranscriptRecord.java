package com.hoppinzq.agent.capability.compact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次压缩操作的元数据记录（原 session 包的 {@code TranscriptRecord}，随能力包迁移）。
 *
 * @author hoppinzq
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptRecord {
    /** 压缩时间（ISO 格式） */
    private String timestamp;
    /** 压缩前的消息数 */
    private int messageCount;
    /** 压缩前的估算 tokens */
    private int estimatedTokens;
    /** 生成的对话摘要 */
    private String summary;
    /** 压缩原因：auto / manual */
    private String reason;
}
