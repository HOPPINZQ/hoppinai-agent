package com.hoppinzq.wyymusic.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.client.LlmMessage;
import com.hoppinzq.agent.client.LlmProvider;
import com.hoppinzq.agent.client.LlmProviders;
import com.hoppinzq.agent.client.LlmRequest;
import com.hoppinzq.agent.client.LlmResponse;
import com.hoppinzq.agent.constant.AIConstants;
import com.hoppinzq.wyymusic.im.WyyImProperties;
import com.hoppinzq.wyymusic.im.model.ImAiReply;
import com.hoppinzq.wyymusic.im.model.ImConversation;
import com.hoppinzq.wyymusic.im.model.ImMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 把私信对话交给 LLM，解析出「回复正文 + 点歌信息」。
 *
 * @author hoppinzq
 */
@Slf4j
@Component
public class WyyImAi {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_OUTPUT_TOKENS = 800;

    private final WyyImProperties properties;
    private final LlmProvider provider;

    public WyyImAi(WyyImProperties properties) {
        this.properties = properties;
        this.provider = LlmProviders.create();
    }

    /**
     * 根据会话上下文生成回复。调用方保证最后一条消息来自对方。
     */
    public ImAiReply reply(ImConversation conversation, List<ImMessage> history) {
        String userPrompt = buildUserPrompt(conversation, history);

        LlmRequest request = LlmRequest.builder()
                .model(AIConstants.MODEL)
                .systemPrompt(properties.effectiveSystemPrompt())
                .messages(new ArrayList<>(List.of(LlmMessage.user(userPrompt))))
                .maxTokens(MAX_OUTPUT_TOKENS)
                .temperature(0.8D)
                .build();

        LlmResponse response = provider.complete(request);
        if (response == null || response.getMessage() == null || response.getMessage().getText() == null
                || response.getMessage().getText().isBlank()) {
            throw new IllegalStateException("AI 没有返回内容");
        }
        ImAiReply reply = parse(response.getMessage().getText());
        if (reply.getReply() == null || reply.getReply().isBlank()) {
            throw new IllegalStateException("AI 返回中缺少 reply 字段: " + response.getMessage().getText());
        }
        return reply;
    }

    private String buildUserPrompt(ImConversation conversation, List<ImMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("【对话对象】")
                .append(conversation.getNickname())
                .append("（uid: ").append(conversation.getUserId()).append("）\n");
        sb.append("【最近私信】（按时间升序，[我] 表示你之前发出去的消息）\n");

        int from = Math.max(0, history.size() - Math.max(1, properties.getMaxHistory()));
        for (int i = from; i < history.size(); i++) {
            ImMessage message = history.get(i);
            sb.append(message.isFromSelf() ? "[我" : "[对方");
            if (message.getTime() > 0) {
                sb.append(' ').append(formatTime(message.getTime()));
            }
            sb.append("] ").append(message.getText()).append('\n');
        }

        sb.append("\n请生成你接下来的回复。只输出 JSON 对象，字段为 reply、songQuery、songReason。");
        return sb.toString();
    }

    private String formatTime(long epochMillis) {
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return TIME_FORMAT.format(time);
    }

    // ============================== 解析 AI 输出 ==============================

    ImAiReply parse(String raw) {
        String cleaned = stripCodeFence(raw);
        JsonNode root = tryParseJsonObject(cleaned);

        String reply = null;
        String songQuery = null;
        String songReason = null;

        if (root != null) {
            reply = firstText(root, "reply", "content", "message", "text");
            songQuery = firstText(root, "songQuery", "song_query", "songName", "song_name", "query");
            songReason = firstText(root, "songReason", "song_reason", "songMsg", "song_msg", "reason");

            JsonNode song = root.get("song");
            if (song != null && !song.isNull()) {
                if (song.isTextual()) {
                    if (isBlank(songQuery)) {
                        songQuery = song.asText();
                    }
                } else if (song.isObject()) {
                    if (isBlank(songQuery)) {
                        songQuery = firstText(song, "query", "song", "title");
                    }
                    if (isBlank(songQuery)) {
                        String name = firstText(song, "name", "songName");
                        String artist = firstText(song, "artist", "singer");
                        if (name != null) {
                            songQuery = isBlank(artist) ? name : name + " " + artist;
                        }
                    }
                    if (isBlank(songReason)) {
                        songReason = firstText(song, "reason", "msg", "message", "desc");
                    }
                }
            }
        }

        songQuery = normalizeNullable(songQuery);
        songReason = normalizeNullable(songReason);

        if (isBlank(reply)) {
            // 模型没按 JSON 输出时，整段文本当作回复内容；是 JSON 但缺少 reply 则视为失败
            reply = (root == null) ? cleaned : null;
        }
        reply = sanitizeReply(reply);

        if (properties.isSendSong() && !isBlank(songQuery)) {
            songQuery = limit(songQuery, 60);
            songReason = isBlank(songReason) ? "这首歌很适合现在的你" : limit(songReason, 60);
        } else {
            songQuery = null;
            songReason = null;
        }

        return ImAiReply.builder()
                .reply(reply)
                .songQuery(songQuery)
                .songReason(songReason)
                .build();
    }

    private static String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
        }
        return text.trim();
    }

    private static JsonNode tryParseJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = lastBalancedBrace(text);
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return MAPPER.readTree(text.substring(start, end + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 找到第一个 '{' 对应的配对 '}' 位置（忽略字符串内部的括号）。
     */
    private static int lastBalancedBrace(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
            if (value.isNumber() || value.isBoolean()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text) || "none".equalsIgnoreCase(text)
                || "undefined".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private String sanitizeReply(String reply) {
        if (isBlank(reply)) {
            return null;
        }
        String text = reply.trim()
                .replaceAll("^[\"']|[\"']$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (isBlank(text)) {
            return null;
        }
        return limit(text, Math.max(20, properties.getMaxReplyLength()));
    }

    private static String limit(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}