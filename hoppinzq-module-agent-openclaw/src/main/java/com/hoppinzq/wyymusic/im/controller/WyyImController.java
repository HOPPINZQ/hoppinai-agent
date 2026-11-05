package com.hoppinzq.wyymusic.im.controller;

import com.hoppinzq.wyymusic.im.WyyImProperties;
import com.hoppinzq.wyymusic.im.model.ImConversation;
import com.hoppinzq.wyymusic.im.model.ImMessage;
import com.hoppinzq.wyymusic.im.model.ImRunResult;
import com.hoppinzq.wyymusic.im.service.WyyImService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网易云私信机器人调试接口。
 * <p>
 * 注意：/run 会真实发送私信，未开启 dryRun 时请谨慎调用。
 *
 * @author hoppinzq
 */
@RestController
@RequestMapping("/wyy/im")
@CrossOrigin(origins = "*")
public class WyyImController {

    private final WyyImService wyyImService;
    private final WyyImProperties properties;

    public WyyImController(WyyImService wyyImService, WyyImProperties properties) {
        this.wyyImService = wyyImService;
        this.properties = properties;
    }

    /**
     * 立即执行一轮：拉取会话 -> AI -> 回复（与定时任务逻辑一致）。
     */
    @PostMapping("/run")
    public ImRunResult run() {
        return wyyImService.runOnce();
    }

    /**
     * 查看会话列表（不发送消息）。
     */
    @GetMapping("/conversations")
    public List<ImConversation> conversations() {
        return wyyImService.listConversations();
    }

    /**
     * 查看与某个用户的聊天记录（不发送消息）。
     */
    @GetMapping("/history")
    public List<ImMessage> history(@RequestParam("uid") long uid) {
        return wyyImService.history(uid);
    }

    /**
     * 查看机器人配置与登录状态。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("dryRun", properties.isDryRun());
        status.put("sendSong", properties.isSendSong());
        status.put("cookieConfigured", properties.getCookie() != null && !properties.getCookie().isBlank());
        status.put("baseUrl", properties.getBaseUrl());
        status.put("pollIntervalMs", properties.getPollIntervalMs());
        status.put("selfUid", wyyImService.selfUid());
        return status;
    }
}