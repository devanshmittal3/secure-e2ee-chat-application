package com.secureconnect.backend.repository;

import com.secureconnect.backend.model.DevicePublicKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DevicePublicKeyRepository extends JpaRepository<DevicePublicKey, UUID> {
    List<DevicePublicKey> findAllByUser_IdIn(List<Long> userIds);
}
