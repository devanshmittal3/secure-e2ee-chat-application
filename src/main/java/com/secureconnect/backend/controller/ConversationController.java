
package com.secureconnect.backend.controller;

import com.secureconnect.backend.model.Conversation;
import com.secureconnect.backend.service.ConversationService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(
            ConversationService conversationService
    ) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public Conversation createConversation(
            @RequestParam Long otherUserId,
            Authentication authentication
    ) {
        return conversationService.createConversation(
                authentication.getName(),
                otherUserId
        );
    }

    @GetMapping
    public List<Conversation> getMyConversations(
            Authentication authentication
    ) {
        return conversationService.getMyConversations(
                authentication.getName()
        );
    }
}
