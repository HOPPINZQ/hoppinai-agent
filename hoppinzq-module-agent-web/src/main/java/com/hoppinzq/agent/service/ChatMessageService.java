package com.hoppinzq.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hoppinzq.agent.entity.ChatMessage;
import com.hoppinzq.agent.mapper.ChatMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;

    private static final Long DEFAULT_USER_ID = 1L;

    public ChatMessageService(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    public void saveMessage(String sessionId, String role, String content, int messageOrder) {
        saveMessage(sessionId, role, content, messageOrder, null);
    }

    public void saveMessage(String sessionId, String role, String content, int messageOrder, Long token) {
        ChatMessage message = new ChatMessage();
        message.setUserId(DEFAULT_USER_ID);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageOrder(messageOrder);
        message.setToken(token);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
    }

    public void updateMessageToken(String sessionId, int messageOrder, Long token) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getUserId, DEFAULT_USER_ID)
               .eq(ChatMessage::getSessionId, sessionId)
               .eq(ChatMessage::getMessageOrder, messageOrder);
        ChatMessage message = chatMessageMapper.selectOne(wrapper);
        if (message != null) {
            message.setToken(token);
            chatMessageMapper.updateById(message);
        }
    }

    public void updateMessage(String sessionId, String role, String content, int messageOrder) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getUserId, DEFAULT_USER_ID)
               .eq(ChatMessage::getSessionId, sessionId)
               .eq(ChatMessage::getRole, role)
               .eq(ChatMessage::getMessageOrder, messageOrder);
        ChatMessage message = chatMessageMapper.selectOne(wrapper);
        if (message != null) {
            message.setContent(content);
            chatMessageMapper.updateById(message);
        }
    }

    public List<ChatMessage> listMessages(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getUserId, DEFAULT_USER_ID)
               .eq(ChatMessage::getSessionId, sessionId)
               .orderByAsc(ChatMessage::getMessageOrder);
        return chatMessageMapper.selectList(wrapper);
    }

    public void deleteMessagesBySession(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getUserId, DEFAULT_USER_ID)
               .eq(ChatMessage::getSessionId, sessionId);
        chatMessageMapper.delete(wrapper);
    }

    public int getNextMessageOrder(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getUserId, DEFAULT_USER_ID)
               .eq(ChatMessage::getSessionId, sessionId)
               .orderByDesc(ChatMessage::getMessageOrder)
               .last("LIMIT 1");
        ChatMessage last = chatMessageMapper.selectOne(wrapper);
        return last != null ? last.getMessageOrder() + 1 : 1;
    }
}
