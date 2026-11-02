package com.secureconnect.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceLinkCreateResponse(
        UUID requestId,
        String pairingCode,
        String pollToken,
        Instant expiresAt
) {
}