
package com.secureconnect.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // New method: generates a token for a particular device session.
    public String generateToken(
            String email,
            Long userId,
            UUID sessionId
    ) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("sessionId", sessionId.toString())
                .issuedAt(new Date())
                .expiration(
                        new Date(System.currentTimeMillis() + expiration)
                )
                .signWith(getSigningKey())
                .compact();
    }

    // Temporary compatibility method.
    // AuthController will be updated to use the new method next.
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(
                        new Date(System.currentTimeMillis() + expiration)
                )
                .signWith(getSigningKey())
                .compact();
    }

    // Extract email from JWT.
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    // Extract account ID from JWT.
    public Long extractUserId(String token) {
        String userId = extractClaims(token)
                .get("userId", String.class);

        return Long.valueOf(userId);
    }

    // Extract device session ID from JWT.
    public UUID extractSessionId(String token) {
        String sessionId = extractClaims(token)
                .get("sessionId", String.class);

        return UUID.fromString(sessionId);
    }

    // Validate signature and expiration while extracting claims.
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }
}
