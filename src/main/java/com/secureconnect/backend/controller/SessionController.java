
package com.secureconnect.backend.controller;

import com.secureconnect.backend.dto.SessionResponse;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.model.UserSession;
import com.secureconnect.backend.repository.UserRepository;
import com.secureconnect.backend.service.SessionService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final UserRepository userRepository;

    public SessionController(
            SessionService sessionService,
            UserRepository userRepository
    ) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
    }

    // Return all sessions belonging to the authenticated user.
    @GetMapping
    public List<SessionResponse> getSessions(
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        UUID currentSessionId = getCurrentSessionId(authentication);

        return sessionService.getUserSessions(user.getId())
                .stream()
                .map(session -> toResponse(
                        session,
                        currentSessionId
                ))
                .toList();
    }

    // Return information about the current device.
    @GetMapping("/current")
    public SessionResponse getCurrentSession(
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        UUID currentSessionId = getCurrentSessionId(authentication);

        UserSession session = sessionService
                .getUserSessions(user.getId())
                .stream()
                .filter(s -> s.getSessionId().equals(currentSessionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Current session not found"
                ));

        return toResponse(session, currentSessionId);
    }

    // Terminate a specific device session.
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> terminateSession(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        UUID currentSessionId = getCurrentSessionId(authentication);

        sessionService.revokeSession(
                user.getId(),
                sessionId,
                currentSessionId
        );

        return ResponseEntity.noContent().build();
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));
    }

    private UUID getCurrentSessionId(Authentication authentication) {
        Object details = authentication.getDetails();

        if (!(details instanceof UUID sessionId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Session information is missing"
            );
        }

        return sessionId;
    }

    private SessionResponse toResponse(
            UserSession session,
            UUID currentSessionId
    ) {
        return new SessionResponse(
                session.getSessionId(),
                session.getDeviceName(),
                session.isPrimary(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getLastActiveAt(),
                session.getSessionId().equals(currentSessionId)
        );
    }
}
