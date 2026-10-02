package com.secureconnect.backend.controller;

import com.secureconnect.backend.model.Conversation;
import com.secureconnect.backend.service.ConversationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public Conversation createConversation(
            @RequestParam Long userId1,
            @RequestParam Long userId2
    ) {
        return conversationService.createConversation(userId1, userId2);
    }
}