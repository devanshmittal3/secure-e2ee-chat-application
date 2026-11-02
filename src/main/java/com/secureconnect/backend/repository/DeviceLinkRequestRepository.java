package com.secureconnect.backend.repository;

import com.secureconnect.backend.model.DeviceLinkRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceLinkRequestRepository
        extends JpaRepository<DeviceLinkRequest, UUID> {

    Optional<DeviceLinkRequest> findByPairingCodeHash(String pairingCodeHash);

    Optional<DeviceLinkRequest> findByPollTokenHash(String pollTokenHash);

    List<DeviceLinkRequest> findAllByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, String status
    );

    List<DeviceLinkRequest> findAllByExpiresAtBeforeAndStatus(
            Instant time, String status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DeviceLinkRequest r WHERE r.pairingCodeHash = :hash")
    Optional<DeviceLinkRequest> findByPairingCodeHashForUpdate(
            @Param("hash") String hash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DeviceLinkRequest r WHERE r.pollTokenHash = :hash")
    Optional<DeviceLinkRequest> findByPollTokenHashForUpdate(
            @Param("hash") String hash
    );
}