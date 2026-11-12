
package com.secureconnect.backend.repository;

import com.secureconnect.backend.model.SessionStatus;
import com.secureconnect.backend.model.UserSession;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository
        extends JpaRepository<UserSession, UUID> {

    List<UserSession> findAllByUser_IdOrderByLastActiveAtDesc(
            Long userId
    );

    List<UserSession> findAllByUser_IdInAndStatus(
            List<Long> userIds, SessionStatus status
    );

    Optional<UserSession> findBySessionIdAndUser_Id(
            UUID sessionId,
            Long userId
    );

    // Lock the matching session row during refresh-token rotation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSession> findByRefreshTokenHash(
            String refreshTokenHash
    );

    Optional<UserSession> findFirstByUser_IdAndPrimaryTrueAndStatus(
            Long userId,
            SessionStatus status
    );

    List<UserSession> findAllByPrimarySessionIdAndStatus(
            UUID primarySessionId,
            SessionStatus status
    );

    List<UserSession> findAllByPrimaryTrueAndStatusAndLastActiveAtBefore(
            SessionStatus status,
            Instant cutoff
    );

    List<UserSession> findAllByPrimaryFalseAndStatusAndLastActiveAtBefore(
            SessionStatus status,
            Instant cutoff
    );
}
