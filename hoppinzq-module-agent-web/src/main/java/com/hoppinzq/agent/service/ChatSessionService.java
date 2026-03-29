package com.hoppinzq.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hoppinzq.agent.entity.ChatSession;
import com.hoppinzq.agent.mapper.ChatSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    private static final Long DEFAULT_USER_ID = 1L;

    public ChatSessionService(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    public ChatSession createSession(String sessionId, String title) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
            return session;
        }
        session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(DEFAULT_USER_ID);
        session.setTitle(title);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
    }

    public void updateSessionTitle(String sessionId, String title) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setTitle(title);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
    }

    public void updateSessionTime(String sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
    }

    public List<ChatSession> listSessions() {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, DEFAULT_USER_ID)
               .orderByDesc(ChatSession::getUpdatedAt);
        return chatSessionMapper.selectList(wrapper);
    }

    public void deleteSession(String sessionId) {
        chatSessionMapper.deleteById(sessionId);
    }

    public ChatSession getBySessionId(String sessionId) {
        return chatSessionMapper.selectById(sessionId);
    }
}
