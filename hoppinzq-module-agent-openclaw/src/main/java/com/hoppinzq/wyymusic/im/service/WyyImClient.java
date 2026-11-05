package com.hoppinzq.wyymusic.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.wyymusic.im.WyyImProperties;
import com.hoppinzq.wyymusic.im.model.ImConversation;
import com.hoppinzq.wyymusic.im.model.ImMessage;
import com.hoppinzq.wyymusic.im.model.ImSong;
import com.hoppinzq.wyymusic.service.WyyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网易云私信接口封装：会话列表、聊天记录、发文本 / 发歌曲、搜索歌曲。
 * <p>
 * NeteaseCloudMusicApi 不同版本的字段名略有差异，这里统一做了兼容解析。
 *
 * @author hoppinzq
 */
@Slf4j
@Component
public class WyyImClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WyyService wyyService;
    private final WyyImProperties properties;

    /** 当前登录账号 uid，首次使用时解析后缓存 */
    private volatile Long selfUid;

    public WyyImClient(WyyService wyyService, WyyImProperties properties) {
        this.wyyService = wyyService;
        this.properties = properties;
    }

    // ============================== 账号 ==============================

    /**
     * 解析当前登录账号的 uid。优先 /user/account，兜底 /login/status。
     */
    public long resolveSelfUid() {
        Long cached = selfUid;
        if (cached != null && cached > 0) {
            return cached;
        }
        try {
            JsonNode account = wyyService.getUserAccount();
            long id = firstLong(account,
                    "account.id", "profile.userId", "data.account.id", "data.profile.userId", "id", "userId");
            if (id > 0) {
                selfUid = id;
                return id;
            }
        } catch (Exception e) {
            log.debug("获取 /user/account 失败，尝试 /login/status: {}", e.getMessage());
        }
        try {
            JsonNode status = wyyService.loginStatus();
            long id = firstLong(status,
                    "data.account.id", "data.profile.userId", "account.id", "profile.userId", "id", "userId");
            if (id > 0) {
                selfUid = id;
                return id;
            }
        } catch (Exception e) {
            log.warn("获取登录账号 uid 失败，请检查 cookie 是否有效: {}", e.getMessage());
        }
        return 0L;
    }

    // ============================== 会话 ==============================

    /**
     * 获取所有会话。合并 /msg/private 与 /msg/recentcontact，按对方 uid 去重。
     */
    public List<ImConversation> listConversations(long selfUid) {
        Map<Long, ImConversation> merged = new LinkedHashMap<>();

        try {
            JsonNode privateRoot = wyyService.getMsgPrivate(properties.getMaxConversations(), 0);
            parseConversations(privateRoot, selfUid, merged);
        } catch (Exception e) {
            log.warn("获取私信会话列表失败: {}", e.getMessage());
        }

        try {
            JsonNode recentRoot = wyyService.getRecentContact();
            parseConversations(recentRoot, selfUid, merged);
        } catch (Exception e) {
            log.debug("获取最近联系人失败: {}", e.getMessage());
        }

        List<ImConversation> conversations = new ArrayList<>(merged.values());
        conversations.sort(Comparator.comparingLong(ImConversation::getLastTime).reversed());
        return conversations;
    }

    private void parseConversations(JsonNode root, long selfUid, Map<Long, ImConversation> out) {
        JsonNode array = findArray(root, 2,
                "msgs", "data", "conversations", "recentContact", "contacts", "records", "list");
        if (array == null || !array.isArray()) {
            return;
        }
        for (JsonNode node : array) {
            if (node == null || !node.isObject()) {
                continue;
            }
            ImConversation conversation = toConversation(node, selfUid);
            if (conversation == null) {
                continue;
            }
            ImConversation old = out.get(conversation.getUserId());
            if (old == null || conversation.getLastTime() >= old.getLastTime()) {
                out.put(conversation.getUserId(), conversation);
            }
        }
    }

    private ImConversation toConversation(JsonNode node, long selfUid) {
        JsonNode fromUser = firstObject(node, "fromUser", "fromUserInfo", "sender", "user");
        JsonNode toUser = firstObject(node, "toUser", "toUserInfo", "receiver");
        long fromId = readUid(fromUser);
        long toId = readUid(toUser);

        long peerId;
        JsonNode peer;
        if (fromId > 0 && fromId == selfUid) {
            peerId = toId;
            peer = toUser;
        } else if (toId > 0 && toId == selfUid) {
            peerId = fromId;
            peer = fromUser;
        } else if (fromId <= 0 && toId <= 0) {
            // 最近联系人等结构：直接挂一个 user / contact 对象，或平铺的 userId
            JsonNode direct = firstObject(node, "user", "peer", "contact", "target");
            long directId = readUid(direct);
            if (directId <= 0) {
                directId = firstLong(node, "userId", "uid", "peerUserId");
            }
            peerId = directId;
            peer = direct;
        } else {
            peerId = firstLong(node, "userId", "uid", "fromUserId", "toUserId", "peerUserId");
            peer = fromId > 0 ? fromUser : toUser;
        }
        if (peerId <= 0 || (selfUid > 0 && peerId == selfUid)) {
            return null;
        }

        String nickname = firstText(peer, "nickname", "nickName", "nick", "name");
        if (nickname == null) {
            nickname = firstText(node, "nickname", "nickName", "nick", "name");
        }
        String avatar = firstText(peer, "avatarUrl", "avatar", "avatarImgUrl", "avatarUrl");
        if (avatar == null) {
            avatar = firstText(node, "avatarUrl", "avatar", "avatarImgUrl");
        }

        long lastTime = firstLong(node, "time", "msgUt", "lastMsgTime", "updateTime", "lastTime", "ut");
        String lastMessage = firstText(node, "lastMsg", "lastMessage", "msg", "content");
        if (lastMessage == null) {
            JsonNode lastMsgNode = node.get("lastMsg");
            if (lastMsgNode != null && lastMsgNode.isObject()) {
                lastMessage = firstText(lastMsgNode, "msg", "text", "title", "content", "name");
            }
        }
        lastMessage = unwrapText(lastMessage);
        int unread = (int) Math.max(0, firstLong(node, "newMsgCount", "unreadCount", "msgCount", "newMsg"));

        return ImConversation.builder()
                .userId(peerId)
                .nickname(nickname == null ? String.valueOf(peerId) : nickname)
                .avatarUrl(avatar)
                .lastMessage(lastMessage == null ? "" : lastMessage)
                .lastTime(lastTime)
                .newMsgCount(unread)
                .build();
    }

    // ============================== 聊天记录 ==============================

    /**
     * 获取与某个用户的私信记录，按时间升序返回。
     */
    public List<ImMessage> history(long selfUid, long peerUid) {
        JsonNode root = wyyService.getMsgPrivateHistory(String.valueOf(peerUid), properties.getMaxHistory(), null);
        JsonNode array = findArray(root, 2,
                "msgs", "data", "messages", "records", "list");
        List<ImMessage> messages = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return messages;
        }
        for (JsonNode node : array) {
            if (node == null || !node.isObject()) {
                continue;
            }
            ImMessage message = toMessage(node, selfUid, peerUid);
            if (message != null) {
                messages.add(message);
            }
        }
        messages.sort(Comparator.comparingLong(ImMessage::getTime));
        return messages;
    }

    private ImMessage toMessage(JsonNode node, long selfUid, long peerUid) {
        JsonNode fromUser = firstObject(node, "fromUser", "fromUserInfo", "sender", "user");
        JsonNode toUser = firstObject(node, "toUser", "toUserInfo", "receiver");
        long fromId = readUid(fromUser);
        long toId = readUid(toUser);
        if (fromId <= 0) {
            fromId = firstLong(node, "fromUserId", "senderId", "uid");
        }
        if (toId <= 0) {
            toId = firstLong(node, "toUserId", "receiverId");
        }

        boolean fromSelf;
        if (fromId > 0 && fromId == selfUid) {
            fromSelf = true;
        } else if (toId > 0 && toId == selfUid) {
            fromSelf = false;
        } else if (fromId == peerUid) {
            fromSelf = false;
        } else if (toId == peerUid) {
            fromSelf = true;
        } else {
            // 无法判断方向时保守跳过，避免误回复自己
            return null;
        }

        String text = firstText(node, "msg", "message", "content", "text");
        text = unwrapText(text);
        int type = (int) firstLong(node, "type", "msgType", "messageType");
        if (text == null || text.isBlank()) {
            text = describeNonText(type);
        }

        long msgId = firstLong(node, "msgId", "msgid", "messageId", "id");
        long time = firstLong(node, "time", "msgUt", "createTime", "timestamp", "ut");
        long senderId = fromSelf ? selfUid : fromId;
        String senderName = firstText(fromUser, "nickname", "nickName", "nick", "name");

        return ImMessage.builder()
                .msgId(msgId)
                .fromSelf(fromSelf)
                .senderId(senderId)
                .senderName(senderName)
                .text(text)
                .time(time)
                .type(type)
                .build();
    }

    /**
     * 私信里分享的歌曲/卡片，msg 可能是一段 JSON 字符串，尽量取出可读文本。
     */
    private String unwrapText(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return text;
        }
        if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("\"") && text.endsWith("\""))) {
            try {
                JsonNode node = MAPPER.readTree(text);
                if (node.isTextual()) {
                    return node.asText();
                }
                if (node.isObject()) {
                    String inner = firstText(node, "msg", "text", "title", "name", "content");
                    if (inner != null && !inner.isBlank()) {
                        return inner;
                    }
                    String songName = firstText(node.path("song"), "name", "songName");
                    if (songName == null) {
                        songName = firstText(node.path("resource"), "name");
                    }
                    if (songName != null) {
                        return "[分享歌曲] " + songName;
                    }
                }
            } catch (Exception ignored) {
                // 不是合法 JSON，按普通文本处理
            }
        }
        return text;
    }

    private String describeNonText(int type) {
        return type == 0 ? "[空消息]" : "[非文本消息 type=" + type + "]";
    }

    // ============================== 发送 ==============================

    public boolean sendText(long userId, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        JsonNode root = wyyService.sendText(String.valueOf(userId), text);
        boolean ok = isOk(root);
        if (!ok) {
            log.warn("发送私信文本失败, uid={}, 响应={}", userId, root);
        }
        return ok;
    }

    public boolean sendSong(long userId, long songId, String msg) {
        JsonNode root = wyyService.sendSong(String.valueOf(userId), String.valueOf(songId),
                msg == null ? "" : msg);
        boolean ok = isOk(root);
        if (!ok) {
            log.warn("发送私信歌曲失败, uid={}, songId={}, 响应={}", userId, songId, root);
        }
        return ok;
    }

    // ============================== 搜歌 ==============================

    /**
     * 按关键词搜索一首歌。优先 /cloudsearch，无结果时兜底 /search。
     */
    public ImSong searchSong(String keywords) {
        List<ImSong> songs = searchSongs(keywords, 1);
        return songs.isEmpty() ? null : songs.get(0);
    }

    /**
     * 按关键词搜索多首歌。优先 /cloudsearch，无结果时兜底 /search。
     */
    public List<ImSong> searchSongs(String keywords, int limit) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        int size = Math.max(1, limit);
        List<ImSong> songs = searchByCloud(keywords, size);
        if (songs.isEmpty()) {
            songs = searchByLegacy(keywords, size);
        }
        return songs;
    }

    private List<ImSong> searchByCloud(String keywords, int limit) {
        try {
            JsonNode root = wyyService.cloudSearch(keywords, limit, 0, 1);
            return parseSongs(root, limit);
        } catch (Exception e) {
            log.warn("云端搜索歌曲失败, keywords={}: {}", keywords, e.getMessage());
            return List.of();
        }
    }

    private List<ImSong> searchByLegacy(String keywords, int limit) {
        try {
            JsonNode root = wyyService.searchMusic(keywords);
            return parseSongs(root, limit);
        } catch (Exception e) {
            log.warn("搜索歌曲失败, keywords={}: {}", keywords, e.getMessage());
            return List.of();
        }
    }

    private List<ImSong> parseSongs(JsonNode root, int limit) {
        if (root == null) {
            return List.of();
        }
        JsonNode songs = root.path("result").path("songs");
        if (!songs.isArray() || songs.isEmpty()) {
            songs = root.path("songs");
        }
        if (!songs.isArray() || songs.isEmpty()) {
            songs = root.path("result").path("song");
        }
        if (!songs.isArray() || songs.isEmpty()) {
            return List.of();
        }
        List<ImSong> result = new ArrayList<>();
        for (JsonNode song : songs) {
            if (result.size() >= limit) {
                break;
            }
            long id = firstLong(song, "id", "songId");
            if (id <= 0) {
                continue;
            }
            String name = firstText(song, "name", "songName", "title");
            if (name == null || name.isBlank()) {
                continue;
            }
            String artist = null;
            JsonNode artists = song.get("ar");
            if (artists == null || !artists.isArray()) {
                artists = song.get("artists");
            }
            if (artists != null && artists.isArray() && !artists.isEmpty()) {
                artist = firstText(artists.get(0), "name");
            }
            if (artist == null) {
                artist = firstText(song.path("album").path("artist"), "name");
            }
            String album = firstText(song.path("al"), "name");
            if (album == null) {
                album = firstText(song.path("album"), "name");
            }
            result.add(ImSong.builder().id(id).name(name).artist(artist).album(album).build());
        }
        return result;
    }

    // ============================== 解析工具 ==============================

    private static boolean isOk(JsonNode root) {
        if (root == null) {
            return false;
        }
        int code = root.path("code").asInt(200);
        if (code == 200) {
            return true;
        }
        JsonNode data = root.get("data");
        return data != null && data.path("code").asInt(0) == 200;
    }

    private static JsonNode findArray(JsonNode node, int depth, String... names) {
        if (node == null || node.isNull() || depth < 0) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (!node.isObject()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        for (String container : new String[]{"data", "result", "body"}) {
            JsonNode value = node.get(container);
            if (value != null && !value.isNull()) {
                JsonNode found = findArray(value, depth - 1, names);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JsonNode firstObject(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isObject()) {
                return value;
            }
        }
        return null;
    }

    private static long readUid(JsonNode user) {
        if (user == null || user.isNull()) {
            return 0L;
        }
        if (user.isNumber()) {
            return user.asLong();
        }
        return firstLong(user, "userId", "uid", "id", "user_id");
    }

    private static long firstLong(JsonNode node, String... paths) {
        if (node == null) {
            return 0L;
        }
        for (String path : paths) {
            JsonNode value = path(node, path);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asLong();
            }
            if (value.isTextual()) {
                try {
                    return Long.parseLong(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // 尝试下一个字段
                }
            }
        }
        return 0L;
    }

    private static String firstText(JsonNode node, String... paths) {
        if (node == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode value = path(node, path);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
            if (value.isNumber()) {
                return value.asText();
            }
        }
        return null;
    }

    private static JsonNode path(JsonNode node, String dottedPath) {
        JsonNode current = node;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }
}