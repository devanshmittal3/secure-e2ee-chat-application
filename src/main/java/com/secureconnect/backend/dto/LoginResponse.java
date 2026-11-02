
package com.secureconnect.backend.dto;

import java.util.UUID;

public class LoginResponse {

    private Long id;
    private String username;
    private String email;

    private String token;
    private UUID sessionId;
    private String refreshToken;
    private String sessionStatus;

    public LoginResponse(
            Long id,
            String username,
            String email,
            String token,
            UUID sessionId,
            String refreshToken,
            String sessionStatus
    ) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.token = token;
        this.sessionId = sessionId;
        this.refreshToken = refreshToken;
        this.sessionStatus = sessionStatus;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }
}
