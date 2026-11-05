package com.hoppinzq.wyymusic.im;

import com.hoppinzq.wyymusic.im.model.ImRunResult;
import com.hoppinzq.wyymusic.im.service.WyyImService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时轮询网易云私信（间隔由 wyy.im.poll-interval-ms 控制）。
 *
 * @author hoppinzq
 */
@Slf4j
@Component
public class WyyImScheduler {

    private final WyyImService wyyImService;

    public WyyImScheduler(WyyImService wyyImService) {
        this.wyyImService = wyyImService;
    }

    @Scheduled(
            fixedDelayString = "${wyy.im.poll-interval-ms:60000}",
            initialDelayString = "${wyy.im.initial-delay-ms:15000}")
    public void poll() {
        try {
            ImRunResult result = wyyImService.runOnce();
            if (result.getRepliedConversations() > 0 || !result.getErrors().isEmpty()) {
                log.info("[wyy-im] 本轮完成: 会话={}, 待回复={}, 已回复={}, 详情={}, 错误={}",
                        result.getScannedConversations(), result.getPendingConversations(),
                        result.getRepliedConversations(), result.getDetails(), result.getErrors());
            } else {
                log.debug("[wyy-im] 本轮完成: 会话={}, 待回复={}", result.getScannedConversations(),
                        result.getPendingConversations());
            }
        } catch (Exception e) {
            log.error("[wyy-im] 定时轮询异常", e);
        }
    }
}