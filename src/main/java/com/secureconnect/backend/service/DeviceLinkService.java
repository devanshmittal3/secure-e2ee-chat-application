
package com.secureconnect.backend.service;

import com.secureconnect.backend.dto.DeviceLinkCreateResponse;
import com.secureconnect.backend.dto.DeviceLinkApprovalResponse;
import com.secureconnect.backend.dto.DeviceLinkPollResponse;

import com.secureconnect.backend.model.User;
import com.secureconnect.backend.model.DeviceLinkRequest;

import com.secureconnect.backend.repository.DeviceLinkRequestRepository;
import com.secureconnect.backend.repository.UserRepository;

import com.secureconnect.backend.security.JwtService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DeviceLinkService {

    private static final String PAIRING_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int PAIRING_CODE_LENGTH = 10;

    private final DeviceLinkRequestRepository repository;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final JwtService jwtService;
    private final long jwtExpiration;

    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceLinkService(
            DeviceLinkRequestRepository repository,
            UserRepository userRepository,
            SessionService sessionService,
            JwtService jwtService,
            @Value("${jwt.expiration}") long jwtExpiration
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.jwtService = jwtService;
        this.jwtExpiration = jwtExpiration;
    }

    // =====================================================
    // 1. PHONE B CREATES A PAIRING REQUEST
    // =====================================================

    @Transactional
    public DeviceLinkCreateResponse createRequest(String deviceName) {

        if (deviceName == null || deviceName.isBlank()) {
            deviceName = "Android device";
        }

        deviceName = deviceName.trim();

        if (deviceName.length() > 120) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Device name must not exceed 120 characters"
            );
        }

        String pairingCode = generatePairingCode();
        String pollToken = generatePollToken();

        DeviceLinkRequest request = new DeviceLinkRequest();

        request.setRequestingDeviceName(deviceName);
        request.setPairingCodeHash(hashSecret(pairingCode));
        request.setPollTokenHash(hashSecret(pollToken));
        request.setStatus("PENDING");

        request.setExpiresAt(
                Instant.now().plus(5, ChronoUnit.MINUTES)
        );

        DeviceLinkRequest saved = repository.save(request);

        // Return the original secrets only once.
        // Never store or log their raw values.

        return new DeviceLinkCreateResponse(
                saved.getRequestId(),
                pairingCode,
                pollToken,
                saved.getExpiresAt()
        );
    }

    // =====================================================
    // 2. PHONE A APPROVES PHONE B
    // =====================================================

    @Transactional
    public DeviceLinkApprovalResponse approveRequest(
            User primaryUser,
            UUID primarySessionId,
            String pairingCode
    ) {

        if (pairingCode == null
                || !pairingCode.trim()
                .matches("[A-HJ-NP-Z2-9]{10}")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pairing code format"
            );
        }

        // Verify that the approving device is the active primary.
        sessionService.requireActivePrimarySession(
                primaryUser.getId(),
                primarySessionId
        );

        String codeHash = hashSecret(
                pairingCode.trim().toUpperCase()
        );

        DeviceLinkRequest request = repository
                .findByPairingCodeHashForUpdate(codeHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid pairing code"
                ));

        Instant now = Instant.now();

        if (!"PENDING".equals(request.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This pairing request has already been handled"
            );
        }

        if (!request.getExpiresAt().isAfter(now)) {

            request.setStatus("EXPIRED");
            repository.save(request);

            return new DeviceLinkApprovalResponse(
                    "EXPIRED",
                    null
            );
        }

        // Associate the pending request with the primary account.
        request.setUserId(primaryUser.getId());
        request.setPrimarySessionId(primarySessionId);

        request.setApprovedAt(now);
        request.setStatus("APPROVED");

        // Phone B gets another five minutes to collect approval.
        request.setExpiresAt(
                now.plus(5, ChronoUnit.MINUTES)
        );

        repository.save(request);

        return new DeviceLinkApprovalResponse(
                "APPROVED",
                now
        );
    }

    // =====================================================
    // 3. PHONE B CHECKS APPROVAL
    // =====================================================

    @Transactional
    public DeviceLinkPollResponse pollRequest(String pollToken) {

        if (pollToken == null || pollToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid polling token"
            );
        }

        String pollHash = hashSecret(pollToken);

        DeviceLinkRequest request = repository
                .findByPollTokenHashForUpdate(pollHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid polling token"
                ));

        Instant now = Instant.now();

        // Expire requests that were not completed in time.
        if (!"COMPLETED".equals(request.getStatus())
                && !"EXPIRED".equals(request.getStatus())
                && !request.getExpiresAt().isAfter(now)) {

            request.setStatus("EXPIRED");
            repository.save(request);

            return emptyPollResponse("EXPIRED");
        }

        if ("PENDING".equals(request.getStatus())) {
            return emptyPollResponse("PENDING");
        }

        if ("EXPIRED".equals(request.getStatus())
                || "REJECTED".equals(request.getStatus())
                || "CANCELLED".equals(request.getStatus())
                || "COMPLETED".equals(request.getStatus())) {

            // Never issue tokens again after completion.
            return emptyPollResponse(request.getStatus());
        }

        if (!"APPROVED".equals(request.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unexpected pairing request status"
            );
        }

        if (request.getUserId() == null
                || request.getPrimarySessionId() == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Approved request is missing account information"
            );
        }

        // Find the account approved by Phone A.
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        // Create Phone B's active secondary session.
        // No active session or refresh token was issued before approval.
        SessionService.IssuedSession issued =
                sessionService.createApprovedSecondarySession(
                        user,
                        request.getRequestingDeviceName(),
                        request.getPrimarySessionId()
                );

        // Generate a JWT containing Phone B's own session ID.
        String accessToken = jwtService.generateToken(
                user.getEmail(),
                user.getId(),
                issued.sessionId()
        );

        Instant tokenExpiresAt =
                Instant.now().plusMillis(jwtExpiration);

        // Mark the request completed before returning credentials.
        request.setStatus("COMPLETED");
        request.setCompletedAt(Instant.now());

        repository.save(request);

        // Credentials are delivered only once.
return new DeviceLinkPollResponse(
        "COMPLETED",
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        issued.sessionId(),
        accessToken,
        issued.refreshToken(),
        tokenExpiresAt
);
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

private DeviceLinkPollResponse emptyPollResponse(String status) {
    return new DeviceLinkPollResponse(
            status,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );
}

    private String generatePairingCode() {

        StringBuilder code =
                new StringBuilder(PAIRING_CODE_LENGTH);

        for (int i = 0; i < PAIRING_CODE_LENGTH; i++) {

            int index = secureRandom.nextInt(
                    PAIRING_ALPHABET.length()
            );

            code.append(PAIRING_ALPHABET.charAt(index));
        }

        return code.toString();
    }

    private String generatePollToken() {

        byte[] tokenBytes = new byte[32];

        secureRandom.nextBytes(tokenBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
    }

    private String hashSecret(String secret) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    secret.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }
}
