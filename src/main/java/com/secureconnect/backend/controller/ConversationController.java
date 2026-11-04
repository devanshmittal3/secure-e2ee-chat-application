
package com.secureconnect.backend.controller;

import com.secureconnect.backend.dto.ConversationSummaryResponse;
import com.secureconnect.backend.model.Conversation;
import com.secureconnect.backend.service.ConversationService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(
            ConversationService conversationService
    ) {
        this.conversationService = conversationService;
    }

    // Create or reuse a one-to-one conversation.
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

    // Get chat summaries for the authenticated user.
    @GetMapping
    public List<ConversationSummaryResponse> getMyConversations(
            Authentication authentication
    ) {
        return conversationService.getMyConversations(
                authentication.getName()
        );
    }

    // Delete conversation only for the current user.
    @DeleteMapping("/{conversationId}/delete-for-me")
    public ResponseEntity<Void> deleteForMe(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        conversationService.deleteForMe(
                authentication.getName(),
                conversationId
        );

        return ResponseEntity.noContent().build();
    }

    // Permanently delete conversation for everyone.
    @DeleteMapping("/{conversationId}/delete-for-everyone")
    public ResponseEntity<Void> deleteForEveryone(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        conversationService.deleteForEveryone(
                authentication.getName(),
                conversationId
        );

        return ResponseEntity.noContent().build();
    }

    // Clear all conversations for the current user.
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Integer>> clearAllForMe(
            Authentication authentication
    ) {
        int clearedChats = conversationService.clearAllForMe(
                authentication.getName()
        );

        return ResponseEntity.ok(
                Map.of("clearedChats", clearedChats)
        );
    }
}
