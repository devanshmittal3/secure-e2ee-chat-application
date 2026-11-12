
package com.secureconnect.backend.controller;

import com.secureconnect.backend.dto.MessageResponse;
import com.secureconnect.backend.dto.EncryptedMessageRequest;
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

    @PostMapping("/encrypted")
    public MessageResponse sendEncryptedMessage(
            @RequestBody EncryptedMessageRequest request,
            Authentication authentication
    ) {
        if (request == null || request.conversationId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "conversationId is required");
        }
        return messageService.sendEncryptedMessage(
                request.conversationId(), authentication.getName(), request.encryptedPayload());
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
