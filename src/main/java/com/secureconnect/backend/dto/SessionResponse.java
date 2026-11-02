
package com.secureconnect.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        String deviceName,
        boolean primary,
        String status,
        Instant createdAt,
        Instant lastActiveAt,
        boolean currentDevice
) {
}
