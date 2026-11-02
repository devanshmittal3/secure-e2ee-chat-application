
package com.secureconnect.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceLinkPollResponse(
        String status,
        Long userId,
        String username,
        String email,
        UUID sessionId,
        String accessToken,
        String refreshToken,
        Instant expiresAt
) {
}
