package com.secureconnect.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_public_keys")
public class DevicePublicKey {
    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "public_key", nullable = false, length = 128)
    private String publicKey;

    @Column(name = "algorithm", nullable = false, length = 40)
    private String algorithm;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DevicePublicKey() {}
    @PrePersist protected void onCreate() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate protected void onUpdate() { updatedAt = Instant.now(); }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
