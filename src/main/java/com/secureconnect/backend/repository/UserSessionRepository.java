
package com.secureconnect.backend.repository;

import com.secureconnect.backend.model.SessionStatus;
import com.secureconnect.backend.model.UserSession;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository
        extends JpaRepository<UserSession, UUID> {

    // Find all sessions belonging to a user.
    List<UserSession> findAllByUser_IdOrderByLastActiveAtDesc(
            Long userId
    );

    // Find a particular session belonging to a particular user.
    Optional<UserSession> findBySessionIdAndUser_Id(
            UUID sessionId,
            Long userId
    );

    // Find a session using its refresh-token hash.
    Optional<UserSession> findByRefreshTokenHash(
            String refreshTokenHash
    );

    // Find the active primary device of a user.
    Optional<UserSession> findFirstByUser_IdAndPrimaryTrueAndStatus(
            Long userId,
            SessionStatus status
    );

    // Find secondary sessions linked to a primary device.
    List<UserSession> findAllByPrimarySessionIdAndStatus(
            UUID primarySessionId,
            SessionStatus status
    );

    // Find active primary devices inactive for more than 14 days.
List<UserSession> findAllByPrimaryTrueAndStatusAndLastActiveAtBefore(
        SessionStatus status,
        Instant cutoff
);

// Find active secondary devices inactive for more than 14 days.
List<UserSession> findAllByPrimaryFalseAndStatusAndLastActiveAtBefore(
        SessionStatus status,
        Instant cutoff
);
}
