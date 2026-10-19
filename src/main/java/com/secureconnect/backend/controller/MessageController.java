
package com.secureconnect.backend.controller;

import com.secureconnect.backend.dto.MessageResponse;
import com.secureconnect.backend.service.MessageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public MessageResponse sendMessage(
            @RequestParam Long conversationId,
            @RequestParam String content,
            Authentication authentication
    ) {
        return messageService.sendMessage(
                conversationId,
                authentication.getName(),
                content
        );
    }

    @GetMapping("/conversation/{conversationId}")
    public List<MessageResponse> getMessagesByConversation(
            @PathVariable Long conversationId,
            Authentication authentication
    ) {
        return messageService.getMessagesByConversation(
                conversationId,
                authentication.getName()
        );
    }
}
