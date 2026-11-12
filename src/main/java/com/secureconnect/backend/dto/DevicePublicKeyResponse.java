package com.secureconnect.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record DevicePublicKeyResponse(
        UUID sessionId, Long userId, String publicKey, String algorithm,
        String fingerprint, Instant updatedAt
) {}
