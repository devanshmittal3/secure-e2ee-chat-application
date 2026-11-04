package com.secureconnect.backend.dto;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long conversationId,
        Long otherUserId,
        String otherUsername,
        String lastMessage,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {}