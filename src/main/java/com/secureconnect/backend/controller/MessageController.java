package com.secureconnect.backend.controller;

import com.secureconnect.backend.model.Message;
import com.secureconnect.backend.service.MessageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public Message sendMessage(
            @RequestParam Long conversationId,
            @RequestParam Long senderId,
            @RequestParam String content
    ) {
        return messageService.sendMessage(
                conversationId,
                senderId,
                content
        );
    }
}