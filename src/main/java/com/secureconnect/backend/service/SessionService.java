
package com.secureconnect.backend.service;

import com.secureconnect.backend.model.SessionStatus;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.model.UserSession;
import com.secureconnect.backend.repository.UserSessionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SessionService {

    private static final int INACTIVITY_DAYS = 14;

    private final UserSessionRepository sessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionService(UserSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    // Create the first active primary device session.
    @Transactional
    public IssuedSession createPrimarySession(
            User user,
            String deviceName
    ) {
        Instant now = Instant.now();

        var existingPrimary =
                sessionRepository.findFirstByUser_IdAndPrimaryTrueAndStatus(
                        user.getId(),
                        SessionStatus.ACTIVE
                );

        if (existingPrimary.isPresent()) {
            UserSession primary = existingPrimary.get();

            if (isInactive(primary, now)) {
                expirePrimaryAndSuspendChildren(primary, now);
            } else {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "An active primary device already exists. Link this device instead."
                );
            }
        }

        return createSession(
                user,
                deviceName,
                true,
                null,
                SessionStatus.ACTIVE
        );
    }

    // Create a pending secondary session.
    // Retained for compatibility with the existing session system.
    @Transactional
    public IssuedSession createPendingSecondarySession(
            User user,
            String deviceName,
            UUID primarySessionId
    ) {
        UserSession primary = sessionRepository.findById(primarySessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Primary device session not found"
                ));

        Instant now = Instant.now();

        if (!primary.getUser().getId().equals(user.getId())
                || !primary.isPrimary()
                || primary.getStatus() != SessionStatus.ACTIVE
                || isInactive(primary, now)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The primary device session is not valid"
            );
        }

        return createSession(
                user,
                deviceName,
                false,
                primarySessionId,
                SessionStatus.PENDING
        );
    }

    // Approve an existing pending secondary session.
    // Retained for compatibility with the existing session system.
    @Transactional
    public void approveSecondarySession(
            Long userId,
            UUID approvingPrimarySessionId,
            UUID secondarySessionId
    ) {
        UserSession primary = sessionRepository
                .findBySessionIdAndUser_Id(
                        approvingPrimarySessionId,
                        userId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Primary session not found"
                ));

        Instant now = Instant.now();

        if (!primary.isPrimary()
                || primary.getStatus() != SessionStatus.ACTIVE
                || isInactive(primary, now)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Primary device is not active"
            );
        }

        UserSession secondary = sessionRepository
                .findBySessionIdAndUser_Id(
                        secondarySessionId,
                        userId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Secondary session not found"
                ));

        if (secondary.isPrimary()
                || secondary.getStatus() != SessionStatus.PENDING
                || !Objects.equals(
                        secondary.getPrimarySessionId(),
                        approvingPrimarySessionId
                )
                || isInactive(secondary, now)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This secondary session cannot be approved"
            );
        }

        secondary.setStatus(SessionStatus.ACTIVE);
        secondary.setLastActiveAt(now);

        sessionRepository.save(secondary);
    }

    // =====================================================
    // NEW: VERIFY ACTIVE PRIMARY DEVICE
    // =====================================================

    @Transactional(readOnly = true)
    public void requireActivePrimarySession(
            Long userId,
            UUID primarySessionId
    ) {
        UserSession primary = sessionRepository
                .findBySessionIdAndUser_Id(
                        primarySessionId,
                        userId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Primary session not found"
                ));

        if (!primary.isPrimary()
                || primary.getStatus() != SessionStatus.ACTIVE
                || isInactive(primary, Instant.now())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "An active primary device is required"
            );
        }
    }

    // =====================================================
    // NEW: CREATE APPROVED SECONDARY DEVICE SESSION
    // =====================================================

    @Transactional
    public IssuedSession createApprovedSecondarySession(
            User user,
            String deviceName,
            UUID primarySessionId
    ) {
        requireActivePrimarySession(
                user.getId(),
                primarySessionId
        );

        return createSession(
                user,
                deviceName,
                false,
                primarySessionId,
                SessionStatus.ACTIVE
        );
    }

    // Validate a session on every authenticated request.
    // Returns false for invalid sessions instead of throwing,
    // so changes to expired sessions can be committed.
    @Transactional
    public boolean validateAndTouch(
            UUID sessionId,
            Long userId
    ) {
        Instant now = Instant.now();

        UserSession session = sessionRepository
                .findBySessionIdAndUser_Id(sessionId, userId)
                .orElse(null);

        if (session == null
                || session.getStatus() != SessionStatus.ACTIVE) {
            return false;
        }

        // Validate the primary device.
        if (session.isPrimary()) {
            if (isInactive(session, now)) {
                expirePrimaryAndSuspendChildren(session, now);
                return false;
            }

            session.setLastActiveAt(now);
            sessionRepository.save(session);
            return true;
        }

        // A secondary device must have a valid primary.
        UUID primaryId = session.getPrimarySessionId();

        if (primaryId == null) {
            suspendSession(
                    session,
                    now,
                    "Primary session reference missing"
            );
            return false;
        }

        UserSession primary = sessionRepository.findById(primaryId)
                .orElse(null);

        if (primary == null
                || !primary.isPrimary()
                || !primary.getUser().getId().equals(userId)) {

            suspendSession(
                    session,
                    now,
                    "Primary session unavailable"
            );
            return false;
        }

        if (primary.getStatus() != SessionStatus.ACTIVE) {
            suspendChildren(
                    primary,
                    now,
                    "Primary device is not active"
            );
            return false;
        }

        // Primary inactivity suspends all its secondary devices.
        if (isInactive(primary, now)) {
            expirePrimaryAndSuspendChildren(primary, now);
            return false;
        }

        // Secondary inactivity expires only that secondary.
        if (isInactive(session, now)) {
            expireSession(
                    session,
                    now,
                    "Device inactive for 14 days"
            );
            return false;
        }

        session.setLastActiveAt(now);
        sessionRepository.save(session);

        return true;
    }

    // List all sessions belonging to a user.
    @Transactional(readOnly = true)
    public List<UserSession> getUserSessions(Long userId) {
        return sessionRepository
                .findAllByUser_IdOrderByLastActiveAtDesc(userId);
    }

    // Revoke a specific session.
    @Transactional
    public void revokeSession(
            Long userId,
            UUID targetSessionId,
            UUID requestingSessionId
    ) {
        UserSession requester = sessionRepository
                .findBySessionIdAndUser_Id(
                        requestingSessionId,
                        userId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Requesting session not found"
                ));

        if (requester.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Requesting session is not active"
            );
        }

        UserSession target = sessionRepository
                .findBySessionIdAndUser_Id(
                        targetSessionId,
                        userId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Target session not found"
                ));

        boolean isSelf =
                target.getSessionId().equals(requester.getSessionId());

        // A secondary device can only terminate its own session.
        // The primary can terminate its own linked secondary devices.
        if (!isSelf) {
            if (!requester.isPrimary()
                    || target.isPrimary()
                    || !Objects.equals(
                            target.getPrimarySessionId(),
                            requester.getSessionId()
                    )) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You cannot terminate this device session"
                );
            }
        }

        if (target.getStatus() == SessionStatus.REVOKED
                || target.getStatus() == SessionStatus.EXPIRED) {
            return;
        }

        Instant now = Instant.now();

        target.setStatus(SessionStatus.REVOKED);
        target.setRevokedAt(now);
        target.setRevocationReason("Logged out by user");

        sessionRepository.save(target);

        if (target.isPrimary()) {
            suspendChildren(
                    target,
                    now,
                    "Primary device logged out"
            );
        }
    }

    // Automatically expire inactive sessions.
    @Transactional
    public void expireInactiveSessions() {

        Instant now = Instant.now();

        Instant cutoff = now.minus(
                INACTIVITY_DAYS,
                ChronoUnit.DAYS
        );

        // 1. Find primary devices inactive for 14 days.
        List<UserSession> inactivePrimaries =
                sessionRepository
                        .findAllByPrimaryTrueAndStatusAndLastActiveAtBefore(
                                SessionStatus.ACTIVE,
                                cutoff
                        );

        for (UserSession primary : inactivePrimaries) {
            expirePrimaryAndSuspendChildren(primary, now);
        }

        // 2. Find secondary devices independently inactive for 14 days.
        List<UserSession> inactiveSecondaries =
                sessionRepository
                        .findAllByPrimaryFalseAndStatusAndLastActiveAtBefore(
                                SessionStatus.ACTIVE,
                                cutoff
                        );

        for (UserSession secondary : inactiveSecondaries) {
            expireSession(
                    secondary,
                    now,
                    "Device inactive for 14 days"
            );
        }
    }

    // Create a session and generate a one-time refresh-token value.
    private IssuedSession createSession(
            User user,
            String deviceName,
            boolean isPrimary,
            UUID primarySessionId,
            SessionStatus status
    ) {
        if (deviceName == null || deviceName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Device name is required"
            );
        }

        String refreshToken = generateRefreshToken();
        String tokenHash = hashRefreshToken(refreshToken);

        Instant now = Instant.now();

        UserSession session = new UserSession();
        session.setUser(user);
        session.setDeviceName(deviceName.trim());
        session.setRefreshTokenHash(tokenHash);
        session.setPrimary(isPrimary);
        session.setPrimarySessionId(primarySessionId);
        session.setStatus(status);
        session.setLastActiveAt(now);

        UserSession saved = sessionRepository.save(session);

        // Return the raw token only once.
        // Never log or store this raw value in the database.
        return new IssuedSession(
                saved.getSessionId(),
                refreshToken,
                saved.getStatus(),
                now
        );
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not hash refresh token",
                    exception
            );
        }
    }

    private boolean isInactive(UserSession session, Instant now) {
        Instant cutoff = now.minus(
                INACTIVITY_DAYS,
                ChronoUnit.DAYS
        );

        return session.getLastActiveAt().isBefore(cutoff);
    }

    private void expirePrimaryAndSuspendChildren(
            UserSession primary,
            Instant now
    ) {
        expireSession(
                primary,
                now,
                "Primary device inactive for 14 days"
        );

        suspendChildren(
                primary,
                now,
                "Primary device inactive for 14 days"
        );
    }

    private void suspendChildren(
            UserSession primary,
            Instant now,
            String reason
    ) {
        List<UserSession> userSessions =
                sessionRepository.findAllByUser_IdOrderByLastActiveAtDesc(
                        primary.getUser().getId()
                );

        for (UserSession child : userSessions) {
            if (Objects.equals(
                        child.getPrimarySessionId(),
                        primary.getSessionId()
                    )
                    && (child.getStatus() == SessionStatus.ACTIVE
                        || child.getStatus() == SessionStatus.PENDING)) {

                suspendSession(child, now, reason);
            }
        }
    }

    private void suspendSession(
            UserSession session,
            Instant now,
            String reason
    ) {
        session.setStatus(SessionStatus.SUSPENDED);
        session.setRevokedAt(now);
        session.setRevocationReason(reason);

        sessionRepository.save(session);
    }

    private void expireSession(
            UserSession session,
            Instant now,
            String reason
    ) {
        session.setStatus(SessionStatus.EXPIRED);
        session.setRevokedAt(now);
        session.setRevocationReason(reason);

        sessionRepository.save(session);
    }

    public record IssuedSession(
            UUID sessionId,
            String refreshToken,
            SessionStatus status,
            Instant createdAt
    ) {
    }
}
