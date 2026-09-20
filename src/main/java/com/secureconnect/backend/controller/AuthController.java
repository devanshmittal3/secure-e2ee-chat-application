package com.secureconnect.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.secureconnect.backend.dto.AuthResponse;
import com.secureconnect.backend.dto.LoginRequest;
import com.secureconnect.backend.dto.LoginResponse;
import com.secureconnect.backend.dto.RegisterRequest;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.security.JwtService;
import com.secureconnect.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {

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
    public LoginResponse login(@RequestBody LoginRequest request) {

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

        String token = jwtService.generateToken(user.getEmail());

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                token
        );
    }
}