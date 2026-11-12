package com.secureconnect.backend.controller;

import com.secureconnect.backend.dto.DevicePublicKeyRequest;
import com.secureconnect.backend.dto.DevicePublicKeyResponse;
import com.secureconnect.backend.model.DevicePublicKey;
import com.secureconnect.backend.model.SessionStatus;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.repository.ConversationMemberRepository;
import com.secureconnect.backend.repository.DevicePublicKeyRepository;
import com.secureconnect.backend.repository.UserRepository;
import com.secureconnect.backend.repository.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/e2ee")
public class E2eeController {
    private static final String SUPPORTED_ALGORITHM = "X25519-XCHACHA20-POLY1305-V1";
    private final DevicePublicKeyRepository keyRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final ConversationMemberRepository memberRepository;

    public E2eeController(DevicePublicKeyRepository keyRepository, UserRepository userRepository,
            UserSessionRepository sessionRepository, ConversationMemberRepository memberRepository) {
        this.keyRepository = keyRepository; this.userRepository = userRepository;
        this.sessionRepository = sessionRepository; this.memberRepository = memberRepository;
    }

    @PutMapping("/device-key")
    public DevicePublicKeyResponse registerDeviceKey(@RequestBody DevicePublicKeyRequest request, Authentication auth) {
        UUID sessionId = currentSession(auth);
        User user = userRepository.findByEmail(auth.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        if (request == null || !SUPPORTED_ALGORITHM.equals(request.algorithm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported E2EE algorithm");
        }
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(request.publicKey()); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public key must be Base64"); }
        if (decoded.length != 32) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X25519 public key must be 32 bytes");
        DevicePublicKey key = keyRepository.findById(sessionId).orElseGet(DevicePublicKey::new);
        key.setSessionId(sessionId); key.setUser(user); key.setPublicKey(request.publicKey()); key.setAlgorithm(SUPPORTED_ALGORITHM);
        return toResponse(keyRepository.save(key));
    }

    @GetMapping("/conversation/{conversationId}/keys")
    public List<DevicePublicKeyResponse> getConversationDeviceKeys(@PathVariable Long conversationId, Authentication auth) {
        User requester = userRepository.findByEmail(auth.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, requester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this conversation");
        }
        List<Long> memberIds = memberRepository.findByConversationId(conversationId).stream()
                .map(m -> m.getUser().getId()).distinct().toList();
        Set<UUID> activeSessions = sessionRepository.findAllByUser_IdInAndStatus(memberIds, SessionStatus.ACTIVE)
                .stream().map(s -> s.getSessionId()).collect(Collectors.toSet());
        return keyRepository.findAllByUser_IdIn(memberIds).stream()
                .filter(k -> activeSessions.contains(k.getSessionId()))
                .map(this::toResponse).toList();
    }

    private UUID currentSession(Authentication auth) {
        if (auth == null || !(auth.getDetails() instanceof UUID id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid device session required");
        }
        return id;
    }

    private DevicePublicKeyResponse toResponse(DevicePublicKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(key.getPublicKey()));
            String fingerprint = HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT).replaceAll("(.{4})", "$1 ").trim();
            return new DevicePublicKeyResponse(key.getSessionId(), key.getUser().getId(), key.getPublicKey(), key.getAlgorithm(), fingerprint, key.getUpdatedAt());
        } catch (Exception ex) { throw new IllegalStateException("Unable to fingerprint public key", ex); }
    }
}
