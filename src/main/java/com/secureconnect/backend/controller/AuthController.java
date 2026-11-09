
package com.secureconnect.backend.controller;

import com.secureconnect.backend.dto.AuthResponse;
import com.secureconnect.backend.dto.LoginRequest;
import com.secureconnect.backend.dto.LoginResponse;
import com.secureconnect.backend.dto.RegisterRequest;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.security.JwtService;
import com.secureconnect.backend.service.SessionService;
import com.secureconnect.backend.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final SessionService sessionService;

    public AuthController(
            UserService userService,
            JwtService jwtService,
            SessionService sessionService
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @PostMapping("/register")
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        if (userService.emailExists(request.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());

        User savedUser = userService.saveUser(user);

        return new AuthResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail()
        );
    }

    @PostMapping("/login")
    public LoginResponse login(
            @RequestBody LoginRequest request,
            @RequestHeader(
                    value = "X-Device-Name",
                    required = false
            ) String deviceName
    ) {
        Optional<User> userOptional =
                userService.authenticate(
                        request.getEmail(),
                        request.getPassword()
                );

        if (userOptional.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        User user = userOptional.get();

        String finalDeviceName =
                (deviceName == null || deviceName.isBlank())
                        ? "Android device"
                        : deviceName.trim();

        SessionService.IssuedSession session =
                sessionService.createPrimarySession(
                        user,
                        finalDeviceName
                );

        String token = jwtService.generateToken(
                user.getEmail(),
                user.getId(),
                session.sessionId()
        );

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                token,
                session.sessionId(),
                session.refreshToken(),
                session.status().name()
        );
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(
            @RequestBody RefreshRequest request
    ) {
        if (request == null || request.getRefreshToken() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Refresh token is required"
            );
        }


SessionService.RefreshedSession refreshed =
        sessionService.refreshWithUser(
                request.getRefreshToken()
        );

SessionService.IssuedSession session = refreshed.session();
User user = refreshed.user();


        String token = jwtService.generateToken(
                user.getEmail(),
                user.getId(),
                session.sessionId()
        );

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                token,
                session.sessionId(),
                session.refreshToken(),
                session.status().name()
        );
    }

    public static class RefreshRequest {
        private String refreshToken;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }
}
