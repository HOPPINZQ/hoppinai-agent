package com.hoppinzq.wyymusic.im.service;

import com.hoppinzq.wyymusic.im.WyyImProperties;
import com.hoppinzq.wyymusic.im.model.ImAiReply;
import com.hoppinzq.wyymusic.im.model.ImConversation;
import com.hoppinzq.wyymusic.im.model.ImMessage;
import com.hoppinzq.wyymusic.im.model.ImRunResult;
import com.hoppinzq.wyymusic.im.model.ImSong;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 网易云私信 AI 机器人的核心流程：
 * <ol>
 *   <li>拉取会话列表（跳过机器人、跳过自己）</li>
 *   <li>逐会话拉取聊天记录，最后一条来自对方才处理（已对话的不管）</li>
 *   <li>把对话交给 AI，得到「回复 + 可选点歌」</li>
 *   <li>发送文本私信；需要时搜索歌曲并发送歌曲私信</li>
 * </ol>
 *
 * @author hoppinzq
 */
@Slf4j
@Service
public class WyyImService {

    private static final int PROCESSED_CACHE_SIZE = 5_000;

    private final WyyImProperties properties;
    private final WyyImClient client;
    private final WyyImAi ai;

    private final ReentrantLock runLock = new ReentrantLock();

    /** 已处理消息的去重缓存（进程内），避免接口延迟导致重复回复 */
    private final Map<String, Boolean> processed = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > PROCESSED_CACHE_SIZE;
                }
            });

    /** 每个会话已知的最后消息时间，用会话列表的时间先做粗筛，减少无意义的 history 请求 */
    private final Map<Long, Long> lastKnownTime = new java.util.concurrent.ConcurrentHashMap<>();

    public WyyImService(WyyImProperties properties, WyyImClient client, WyyImAi ai) {
        this.properties = properties;
        this.client = client;
        this.ai = ai;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("[wyy-im] 私信自动回复未开启（wyy.im.enabled=false）");
            return;
        }
        if (isBlank(properties.getCookie())) {
            log.warn("[wyy-im] 未配置 Cookie，私信自动回复不会执行。请设置环境变量 WYY_COOKIE 或配置 wyy.im.cookie");
            return;
        }
        log.info("[wyy-im] 私信自动回复已开启: baseUrl={}, 轮询间隔={}ms, dryRun={}, 推荐歌曲={}",
                properties.getBaseUrl(), properties.getPollIntervalMs(), properties.isDryRun(), properties.isSendSong());
    }

    /**
     * 执行一轮：扫描 -> AI -> 回复。
     */
    public ImRunResult runOnce() {
        ImRunResult result = ImRunResult.builder()
                .enabled(properties.isEnabled())
                .dryRun(properties.isDryRun())
                .details(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();

        if (!properties.isEnabled()) {
            result.getDetails().add("私信自动回复未开启");
            return result;
        }
        if (isBlank(properties.getCookie())) {
            result.getDetails().add("未配置 Cookie，跳过");
            return result;
        }
        if (!runLock.tryLock()) {
            result.getDetails().add("上一轮仍在执行，跳过本次");
            return result;
        }

        try {
            long selfUid = client.resolveSelfUid();
            result.setSelfUid(selfUid);
            if (selfUid <= 0) {
                result.getErrors().add("无法解析当前登录账号 uid，请检查 Cookie 是否有效");
                return result;
            }

            List<ImConversation> conversations = client.listConversations(selfUid);
            result.setScannedConversations(conversations.size());

            int handled = 0;
            for (ImConversation conversation : conversations) {
                if (handled >= Math.max(1, properties.getMaxConversations())) {
                    break;
                }
                if (conversation.getUserId() <= 0 || conversation.getUserId() == selfUid) {
                    continue;
                }
                if (isBot(conversation)) {
                    log.debug("[wyy-im] 跳过机器人会话: {} ({})", conversation.getNickname(), conversation.getUserId());
                    continue;
                }
                handled++;
                processConversation(selfUid, conversation, result);
            }
        } catch (Exception e) {
            log.error("[wyy-im] 本轮执行失败", e);
            result.getErrors().add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            runLock.unlock();
        }
        return result;
    }

    private void processConversation(long selfUid, ImConversation conversation, ImRunResult result) {
        long peerUid = conversation.getUserId();
        String who = safeName(conversation) + "(" + peerUid + ")";
        long listTime = conversation.getLastTime();

        // 粗筛：会话列表没有任何新动静时，不再拉取 history
        Long known = lastKnownTime.get(peerUid);
        if (conversation.getNewMsgCount() <= 0 && listTime > 0 && known != null && listTime <= known) {
            return;
        }

        List<ImMessage> history;
        try {
            history = client.history(selfUid, peerUid);
        } catch (Exception e) {
            log.warn("[wyy-im] 拉取聊天记录失败 {}: {}", who, e.getMessage());
            result.getErrors().add("拉取聊天记录失败 " + who + ": " + e.getMessage());
            return;
        }
        if (history.isEmpty()) {
            if (listTime > 0) {
                lastKnownTime.merge(peerUid, listTime, Math::max);
            }
            return;
        }
        lastKnownTime.merge(peerUid, Math.max(listTime, history.get(history.size() - 1).getTime()), Math::max);

        ImMessage last = history.get(history.size() - 1);
        if (last.isFromSelf()) {
            // 已对话的不管：最后一条是我们发出的
            return;
        }

        String key = messageKey(peerUid, last);
        if (processed.containsKey(key)) {
            return;
        }

        long ageMinutes = properties.getMaxAgeMinutes();
        if (ageMinutes > 0 && last.getTime() > 0
                && System.currentTimeMillis() - last.getTime() > ageMinutes * 60_000L) {
            // 旧消息不再打扰
            markProcessed(key);
            return;
        }

        result.setPendingConversations(result.getPendingConversations() + 1);

        ImAiReply aiReply;
        try {
            aiReply = ai.reply(conversation, history);
        } catch (Exception e) {
            log.warn("[wyy-im] AI 生成回复失败 {}: {}", who, e.getMessage());
            result.getErrors().add("AI 生成回复失败 " + who + ": " + e.getMessage());
            // 允许下一轮重试
            lastKnownTime.remove(peerUid);
            return;
        }
        if (aiReply == null || isBlank(aiReply.getReply())) {
            result.getErrors().add("AI 未生成有效回复 " + who);
            lastKnownTime.remove(peerUid);
            return;
        }

        if (properties.isDryRun()) {
            result.getDetails().add("[演练] " + who + " -> " + preview(aiReply.getReply())
                    + (isBlank(aiReply.getSongQuery()) ? "" : " | 点歌: " + aiReply.getSongQuery()));
            return;
        }

        boolean sent = client.sendText(peerUid, aiReply.getReply());
        if (!sent) {
            result.getErrors().add("发送私信失败 " + who);
            // 允许下一轮重试
            lastKnownTime.remove(peerUid);
            return;
        }
        markProcessed(key);
        result.setRepliedConversations(result.getRepliedConversations() + 1);
        result.getDetails().add("已回复 " + who + ": " + preview(aiReply.getReply()));

        if (!isBlank(aiReply.getSongQuery())) {
            ImSong song = client.searchSong(aiReply.getSongQuery());
            if (song == null) {
                result.getDetails().add("未搜索到歌曲: " + aiReply.getSongQuery());
            } else {
                boolean songSent = client.sendSong(peerUid, song.getId(), aiReply.getSongReason());
                if (songSent) {
                    result.getDetails().add("已推荐歌曲 " + song.getName()
                            + (isBlank(song.getArtist()) ? "" : " - " + song.getArtist())
                            + " (id=" + song.getId() + ")");
                } else {
                    result.getErrors().add("发送歌曲失败 " + who + " songId=" + song.getId());
                }
            }
        }
    }

    // ============================== 手动调试接口支持 ==============================

    public long selfUid() {
        return client.resolveSelfUid();
    }

    public List<ImConversation> listConversations() {
        long selfUid = client.resolveSelfUid();
        if (selfUid <= 0) {
            return List.of();
        }
        return client.listConversations(selfUid);
    }

    public List<ImMessage> history(long peerUid) {
        long selfUid = client.resolveSelfUid();
        if (selfUid <= 0) {
            return List.of();
        }
        return client.history(selfUid, peerUid);
    }

    // ============================== 内部工具 ==============================

    private boolean isBot(ImConversation conversation) {
        List<Long> botIds = properties.getBotUserIds();
        if (botIds != null && botIds.contains(conversation.getUserId())) {
            return true;
        }
        String nickname = conversation.getNickname();
        List<String> keywords = properties.getBotNicknameKeywords();
        if (nickname != null && keywords != null) {
            String lower = nickname.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (!isBlank(keyword) && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String messageKey(long peerUid, ImMessage message) {
        if (message.getMsgId() > 0) {
            return "msg:" + message.getMsgId();
        }
        String text = message.getText() == null ? "" : message.getText();
        return "peer:" + peerUid + ":" + message.getTime() + ":" + Integer.toHexString(text.hashCode());
    }

    private void markProcessed(String key) {
        processed.put(key, Boolean.TRUE);
    }

    private static String safeName(ImConversation conversation) {
        return isBlank(conversation.getNickname()) ? String.valueOf(conversation.getUserId()) : conversation.getNickname();
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 40 ? text : text.substring(0, 40) + "...";
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}