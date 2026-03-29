package com.hoppinzq.controller;

import com.hoppinzq.agent.entity.ChatMessage;
import com.hoppinzq.agent.entity.ChatSession;
import com.hoppinzq.agent.service.ChatMessageService;
import com.hoppinzq.agent.service.ChatSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatHistoryController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    public ChatHistoryController(ChatSessionService chatSessionService,
                                 ChatMessageService chatMessageService) {
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> listSessions() {
        return ResponseEntity.ok(chatSessionService.listSessions());
    }

    @PostMapping("/sessions")
    public ResponseEntity<ChatSession> createSession(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String title = body.getOrDefault("title", "新会话");
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ChatSession session = chatSessionService.createSession(sessionId, title);
        return ResponseEntity.ok(session);
    }

    @PutMapping("/sessions/{sessionId}/title")
    public ResponseEntity<Void> updateTitle(@PathVariable String sessionId,
                                            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        chatSessionService.updateSessionTitle(sessionId, title);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        chatMessageService.deleteMessagesBySession(sessionId);
        chatSessionService.deleteSession(sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> listMessages(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatMessageService.listMessages(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> saveMessage(@PathVariable String sessionId,
                                                           @RequestBody Map<String, Object> body) {
        String role = (String) body.get("role");
        String content = (String) body.get("content");
        if (role == null || content == null) {
            return ResponseEntity.badRequest().build();
        }
        int order = chatMessageService.getNextMessageOrder(sessionId);
        chatMessageService.saveMessage(sessionId, role, content, order);
        chatSessionService.updateSessionTime(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("messageOrder", order);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Void> updateMessage(@PathVariable String sessionId,
                                              @RequestBody Map<String, Object> body) {
        String role = (String) body.get("role");
        String content = (String) body.get("content");
        Object orderObj = body.get("messageOrder");
        if (role == null || content == null || orderObj == null) {
            return ResponseEntity.badRequest().build();
        }
        int order = (orderObj instanceof Number) ? ((Number) orderObj).intValue() : Integer.parseInt(orderObj.toString());
        chatMessageService.updateMessage(sessionId, role, content, order);
        return ResponseEntity.ok().build();
    }
}
