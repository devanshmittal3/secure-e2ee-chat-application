package com.secureconnect.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_link_requests", schema = "public")
public class DeviceLinkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "primary_session_id")
    private UUID primarySessionId;

    @Column(name = "requesting_device_name", nullable = false, length = 120)
    private String requestingDeviceName;

    @Column(name = "pairing_code_hash", nullable = false, unique = true, length = 64)
    private String pairingCodeHash;

    @Column(name = "poll_token_hash", nullable = false, unique = true, length = 64)
    private String pollTokenHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public DeviceLinkRequest() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }

    public UUID getRequestId() {
        return requestId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UUID getPrimarySessionId() {
        return primarySessionId;
    }

    public void setPrimarySessionId(UUID primarySessionId) {
        this.primarySessionId = primarySessionId;
    }

    public String getRequestingDeviceName() {
        return requestingDeviceName;
    }

    public void setRequestingDeviceName(String requestingDeviceName) {
        this.requestingDeviceName = requestingDeviceName;
    }

    public String getPairingCodeHash() {
        return pairingCodeHash;
    }

    public void setPairingCodeHash(String pairingCodeHash) {
        this.pairingCodeHash = pairingCodeHash;
    }

    public String getPollTokenHash() {
        return pollTokenHash;
    }

    public void setPollTokenHash(String pollTokenHash) {
        this.pollTokenHash = pollTokenHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}