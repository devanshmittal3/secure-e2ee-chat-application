
package com.secureconnect.backend.security;

import com.secureconnect.backend.service.SessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.jsonwebtoken.JwtException;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            SessionService sessionService
    ) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Allow requests without a token to continue.
        // Spring Security will decide whether authentication is required.
        if (authHeader == null || authHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authHeader.startsWith("Bearer ")) {
            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Invalid authorization header"
            );
            return;
        }

        String token = authHeader.substring(7).trim();

        if (token.isEmpty()) {
            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Missing access token"
            );
            return;
        }

        String email;
        Long userId;
        UUID sessionId;

        // Step 1: Validate JWT and extract account/device information.
        try {
            email = jwtService.extractEmail(token);
            userId = jwtService.extractUserId(token);
            sessionId = jwtService.extractSessionId(token);

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Missing email claim");
            }

        } catch (JwtException | IllegalArgumentException exception) {

            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Invalid or expired access token"
            );

            return;
        } catch (RuntimeException exception) {

            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Invalid access token claims"
            );

            return;
        }

        // Step 2: Validate the device session in PostgreSQL.
        boolean validSession;

        try {
            validSession = sessionService.validateAndTouch(
                    sessionId,
                    userId
            );

        } catch (Exception exception) {

            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Session validation is temporarily unavailable"
            );

            return;
        }

        // Step 3: Reject expired, suspended or revoked sessions.
        if (!validSession) {

            SecurityContextHolder.clearContext();

            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Device session is invalid, expired or revoked"
            );

            return;
        }

        // Step 4: Set the authenticated user for the request.
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        Collections.emptyList()
                );
        authentication.setDetails(sessionId);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
