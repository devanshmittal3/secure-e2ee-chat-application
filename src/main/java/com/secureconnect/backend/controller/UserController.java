
package com.secureconnect.backend.controller;

import com.secureconnect.backend.dto.UserResponse;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<UserResponse> getAllUsers(
            Authentication authentication
    ) {
        String currentEmail = authentication.getName();

        return userRepository.findAll()
                .stream()
                .filter(user ->
                        !user.getEmail().equals(currentEmail)
                )
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getUsername()
                ))
                .toList();
    }
}
