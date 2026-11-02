package com.secureconnect.backend.dto;

import java.time.Instant;

public record DeviceLinkApprovalResponse(
        String status,
        Instant approvedAt
) {
}