package com.secureconnect.backend.dto;

public record EncryptedMessageRequest(Long conversationId, String encryptedPayload) {}
