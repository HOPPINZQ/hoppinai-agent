package com.hoppinzq.wyymusic.im.tool;

import com.hoppinzq.agent.tool.ToolDefinition;
import com.hoppinzq.wyymusic.im.WyyImProperties;
import com.hoppinzq.wyymusic.im.model.ImConversation;
import com.hoppinzq.wyymusic.im.model.ImMessage;
import com.hoppinzq.wyymusic.im.model.ImRunResult;
import com.hoppinzq.wyymusic.im.model.ImSong;
import com.hoppinzq.wyymusic.im.service.WyyImClient;
import com.hoppinzq.wyymusic.im.service.WyyImService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hoppinzq.agent.constant.AIConstants.OBJECT_MAPPER;

/**
 * 把网易云私信能力注册成 agent 工具。
 * <p>
 * 供主对话 agent（/agent/runTask、/agent/streamChat）使用：
 * 查看会话 -> 查看聊天记录 -> 搜索歌曲 -> 发送文本/歌曲，或直接触发一轮自动回复。
 *
 * @author hoppinzq
 */
public final class WyyImTools {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private WyyImTools() {
    }

    public static List<ToolDefinition> create(WyyImClient client, WyyImService service,
                                              WyyImProperties properties) {
        List<ToolDefinition> tools = new ArrayList<>();

        tools.add(new ToolDefinition(
                "wyy_im_conversations",
                "查看当前登录的网易云账号的私信会话列表。返回每个会话的对方 uid、昵称、未读数和最后一条消息。" +
                        "回复私信前应先调用此工具找到对方 uid。",
                ToolDefinition.createInputSchema(
                        Map.of("limit", ToolDefinition.createProperty("integer", "最多返回多少个会话，默认 20")),
                        List.of()
                ),
                WyyImToolInputs.Conversations.class,
                wrap(input -> {
                    WyyImToolInputs.Conversations in = parse(input, WyyImToolInputs.Conversations.class);
                    return conversations(client, in.getLimit());
                })
        ));

        tools.add(new ToolDefinition(
                "wyy_im_history",
                "查看与某个网易云用户的私信聊天记录。uid 来自 wyy_im_conversations 返回的会话 uid。" +
                        "回复前先读记录，理解上下文。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "uid", ToolDefinition.createProperty("integer", "对方用户 id，必填"),
                                "limit", ToolDefinition.createProperty("integer", "最多返回多少条消息，默认 20")
                        ),
                        List.of("uid")
                ),
                WyyImToolInputs.History.class,
                wrap(input -> {
                    WyyImToolInputs.History in = parse(input, WyyImToolInputs.History.class);
                    return history(client, in);
                })
        ));

        tools.add(new ToolDefinition(
                "wyy_search_song",
                "在网易云音乐搜索歌曲，返回歌曲 id、歌名、歌手，可用于 wyy_send_song 的 songId。" +
                        "用户想听歌、求推荐、描述心情时使用。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "keywords", ToolDefinition.createProperty("string", "搜索关键词，如「周杰伦 晴天」或「适合雨天的民谣」"),
                                "limit", ToolDefinition.createProperty("integer", "返回数量，默认 5，最多 10")
                        ),
                        List.of("keywords")
                ),
                WyyImToolInputs.SearchSong.class,
                wrap(input -> {
                    WyyImToolInputs.SearchSong in = parse(input, WyyImToolInputs.SearchSong.class);
                    return searchSongs(client, in);
                })
        ));

        tools.add(new ToolDefinition(
                "wyy_send_text",
                "以当前登录的网易云账号向指定用户发送一条文本私信。这是真实发送操作，发送前必须确认 uid 和内容。" +
                        "dry-run 模式下只会返回拟发送内容，不会真正发送。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "uid", ToolDefinition.createProperty("integer", "对方用户 id，必填"),
                                "msg", ToolDefinition.createProperty("string", "私信内容，必填")
                        ),
                        List.of("uid", "msg")
                ),
                WyyImToolInputs.SendText.class,
                wrap(input -> {
                    WyyImToolInputs.SendText in = parse(input, WyyImToolInputs.SendText.class);
                    return sendText(client, properties, in);
                })
        ));

        tools.add(new ToolDefinition(
                "wyy_send_song",
                "以当前登录的网易云账号向指定用户发送一首歌曲私信。传 songId 直接发送；也可以传 keywords，由工具先搜索再发送第一首。" +
                        "这是真实发送操作，dry-run 模式下不会真正发送。",
                ToolDefinition.createInputSchema(
                        Map.of(
                                "uid", ToolDefinition.createProperty("integer", "对方用户 id，必填"),
                                "songId", ToolDefinition.createProperty("integer", "歌曲 id；与 keywords 二选一"),
                                "keywords", ToolDefinition.createProperty("string", "搜歌关键词；songId 为空时使用"),
                                "msg", ToolDefinition.createProperty("string", "随歌曲发送的一句话")
                        ),
                        List.of("uid")
                ),
                WyyImToolInputs.SendSong.class,
                wrap(input -> {
                    WyyImToolInputs.SendSong in = parse(input, WyyImToolInputs.SendSong.class);
                    return sendSong(client, properties, in);
                })
        ));

        tools.add(new ToolDefinition(
                "wyy_im_auto_reply",
                "立即执行一轮私信自动回复：拉取所有未回复会话，交给 AI 生成回复，并发送文本/歌曲。" +
                        "与定时任务逻辑一致。用户说「处理一下私信」「帮我回复未读消息」时使用。",
                ToolDefinition.createInputSchema(Map.of(), List.of()),
                WyyImToolInputs.Empty.class,
                wrap(input -> autoReply(service))
        ));

        return tools;
    }

    // ============================== 工具实现 ==============================

    private static String conversations(WyyImClient client, Integer limit) {
        long selfUid = client.resolveSelfUid();
        if (selfUid <= 0) {
            return "错误：无法获取当前网易云登录账号，请检查 wyy.im.cookie 是否有效";
        }
        List<ImConversation> conversations = client.listConversations(selfUid);
        if (conversations.isEmpty()) {
            return "当前账号（uid=" + selfUid + "）暂无私信会话";
        }
        int size = clamp(limit, 20, 1, 50);
        StringBuilder sb = new StringBuilder("当前账号 uid=").append(selfUid)
                .append("，共 ").append(conversations.size()).append(" 个会话：\n");
        for (int i = 0; i < Math.min(size, conversations.size()); i++) {
            ImConversation conversation = conversations.get(i);
            sb.append(i + 1).append(". uid=").append(conversation.getUserId())
                    .append(" 昵称=").append(conversation.getNickname())
                    .append(" 未读=").append(conversation.getNewMsgCount())
                    .append(" 最后消息=").append(truncate(conversation.getLastMessage(), 80));
            if (conversation.getLastTime() > 0) {
                sb.append(" 时间=").append(formatTime(conversation.getLastTime()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String history(WyyImClient client, WyyImToolInputs.History in) {
        long selfUid = client.resolveSelfUid();
        if (selfUid <= 0) {
            return "错误：无法获取当前网易云登录账号，请检查 wyy.im.cookie 是否有效";
        }
        if (in.getUid() == null || in.getUid() <= 0) {
            return "错误：uid 必填，可先调用 wyy_im_conversations 获取";
        }
        int size = clamp(in.getLimit(), 20, 1, 100);
        List<ImMessage> messages = client.history(selfUid, in.getUid());
        if (messages.isEmpty()) {
            return "与 uid=" + in.getUid() + " 没有聊天记录";
        }
        int from = Math.max(0, messages.size() - size);
        StringBuilder sb = new StringBuilder("与 uid=").append(in.getUid())
                .append(" 的最近 ").append(messages.size() - from).append(" 条私信：\n");
        for (int i = from; i < messages.size(); i++) {
            ImMessage message = messages.get(i);
            sb.append('[').append(formatTime(message.getTime())).append("] ")
                    .append(message.isFromSelf() ? "我" : "对方")
                    .append(": ").append(truncate(message.getText(), 200)).append('\n');
        }
        return sb.toString();
    }

    private static String searchSongs(WyyImClient client, WyyImToolInputs.SearchSong in) {
        String keywords = in.getKeywords() == null ? "" : in.getKeywords().trim();
        if (keywords.isEmpty()) {
            return "错误：keywords 必填";
        }
        int size = clamp(in.getLimit(), 5, 1, 10);
        List<ImSong> songs = client.searchSongs(keywords, size);
        if (songs.isEmpty()) {
            return "没有搜索到歌曲：" + keywords;
        }
        StringBuilder sb = new StringBuilder("搜索「").append(keywords).append("」结果：\n");
        for (int i = 0; i < songs.size(); i++) {
            ImSong song = songs.get(i);
            sb.append(i + 1).append(". id=").append(song.getId())
                    .append(" 《").append(song.getName()).append('》');
            if (song.getArtist() != null) {
                sb.append(" - ").append(song.getArtist());
            }
            if (song.getAlbum() != null) {
                sb.append("（专辑：").append(song.getAlbum()).append('）');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String sendText(WyyImClient client, WyyImProperties properties, WyyImToolInputs.SendText in) {
        long uid = in.getUid() == null ? 0 : in.getUid();
        String msg = in.getMsg() == null ? "" : in.getMsg().trim();
        if (uid <= 0) {
            return "错误：uid 必填";
        }
        if (msg.isEmpty()) {
            return "错误：msg 必填";
        }
        if (properties.isDryRun()) {
            return "当前为 dry-run 演练模式，未发送。拟发送给 uid=" + uid + "：" + msg;
        }
        boolean ok = client.sendText(uid, msg);
        return ok ? "已向 uid=" + uid + " 发送私信：" + msg
                : "发送失败，请检查 Cookie 是否有效或网易云接口是否可用";
    }

    private static String sendSong(WyyImClient client, WyyImProperties properties, WyyImToolInputs.SendSong in) {
        long uid = in.getUid() == null ? 0 : in.getUid();
        if (uid <= 0) {
            return "错误：uid 必填";
        }

        ImSong song;
        if (in.getSongId() != null && in.getSongId() > 0) {
            song = ImSong.builder().id(in.getSongId()).name("歌曲 id=" + in.getSongId()).build();
        } else {
            String keywords = in.getKeywords() == null ? "" : in.getKeywords().trim();
            if (keywords.isEmpty()) {
                return "错误：songId 与 keywords 至少填写一个";
            }
            song = client.searchSong(keywords);
            if (song == null) {
                return "没有搜索到歌曲：" + keywords;
            }
        }

        String msg = in.getMsg() == null ? "" : in.getMsg().trim();
        String desc = songDesc(song);
        if (properties.isDryRun()) {
            return "当前为 dry-run 演练模式，未发送。拟发送歌曲给 uid=" + uid + "：" + desc;
        }
        boolean ok = client.sendSong(uid, song.getId(), msg);
        return ok ? "已向 uid=" + uid + " 发送歌曲：" + desc
                : "发送歌曲失败，请检查 Cookie 是否有效或网易云接口是否可用";
    }

    private static String autoReply(WyyImService service) {
        ImRunResult result = service.runOnce();
        StringBuilder sb = new StringBuilder("私信自动回复执行完成：\n")
                .append("- 扫描会话: ").append(result.getScannedConversations()).append('\n')
                .append("- 待回复: ").append(result.getPendingConversations()).append('\n')
                .append("- 已回复: ").append(result.getRepliedConversations()).append('\n');
        if (result.getDetails() != null && !result.getDetails().isEmpty()) {
            sb.append("- 详情: ").append(String.join("；", result.getDetails())).append('\n');
        }
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            sb.append("- 错误: ").append(String.join("；", result.getErrors())).append('\n');
        }
        return sb.toString();
    }

    // ============================== 工具函数适配 ==============================

    @FunctionalInterface
    private interface ToolBody {
        String run(String input) throws Exception;
    }

    private static Function<String, String> wrap(ToolBody body) {
        return input -> {
            try {
                return body.run(input);
            } catch (Exception e) {
                return "参数错误: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        };
    }

    private static <T> T parse(String input, Class<T> type) throws Exception {
        return OBJECT_MAPPER.readValue(input == null || input.isBlank() ? "{}" : input, type);
    }

    private static int clamp(Integer value, int defaultValue, int min, int max) {
        int size = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, size));
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return TIME_FORMAT.format(time);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').trim();
        return singleLine.length() <= max ? singleLine : singleLine.substring(0, max) + "...";
    }

    private static String songDesc(ImSong song) {
        StringBuilder sb = new StringBuilder("《").append(song.getName()).append('》');
        if (song.getArtist() != null) {
            sb.append(" - ").append(song.getArtist());
        }
        sb.append(" (id=").append(song.getId()).append(')');
        return sb.toString();
    }
}