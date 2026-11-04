package com.secureconnect.backend.dto;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long id,
        Long otherUserId,
        String otherUsername,
        String lastMessage,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {}