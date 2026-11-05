package com.hoppinzq.wyymusic.im.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次私信轮询的执行结果。
 *
 * @author hoppinzq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImRunResult {

    private boolean enabled;
    private boolean dryRun;
    private long selfUid;
    /** 拉取到的会话数 */
    private int scannedConversations;
    /** 其中需要回复的会话数 */
    private int pendingConversations;
    /** 成功回复的会话数 */
    private int repliedConversations;
    private List<String> details;
    private List<String> errors;

    public static ImRunResult skipped(String reason) {
        ImRunResult result = ImRunResult.builder()
                .enabled(false)
                .details(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();
        result.getDetails().add(reason);
        return result;
    }
}