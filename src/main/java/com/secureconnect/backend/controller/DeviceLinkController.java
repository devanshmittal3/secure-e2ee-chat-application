
package com.secureconnect.backend.controller;

import com.secureconnect.backend.dto.DeviceLinkCreateRequest;
import com.secureconnect.backend.dto.DeviceLinkCreateResponse;
import com.secureconnect.backend.dto.DeviceLinkApproveRequest;
import com.secureconnect.backend.dto.DeviceLinkApprovalResponse;
import com.secureconnect.backend.dto.DeviceLinkPollRequest;
import com.secureconnect.backend.dto.DeviceLinkPollResponse;

import com.secureconnect.backend.model.User;
import com.secureconnect.backend.repository.UserRepository;
import com.secureconnect.backend.service.DeviceLinkService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/device-link")
public class DeviceLinkController {

    private final DeviceLinkService deviceLinkService;
    private final UserRepository userRepository;

    public DeviceLinkController(
            DeviceLinkService deviceLinkService,
            UserRepository userRepository
    ) {
        this.deviceLinkService = deviceLinkService;
        this.userRepository = userRepository;
    }

    // Phone B: Create a pairing request.
    @PostMapping("/requests")
    public ResponseEntity<DeviceLinkCreateResponse> createRequest(
            @RequestBody DeviceLinkCreateRequest request
    ) {
        DeviceLinkCreateResponse response =
                deviceLinkService.createRequest(request.deviceName());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Phone A: Approve a pairing request.
    @PostMapping("/approve")
    public ResponseEntity<DeviceLinkApprovalResponse> approveRequest(
            @RequestBody DeviceLinkApproveRequest request,
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // Get the session ID from the authenticated JWT.
        Object details = authentication.getDetails();

        if (!(details instanceof UUID primarySessionId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid primary session"
            );
        }

        // Resolve the authenticated account from its email.
        User primaryUser = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User account not found"
                ));

        DeviceLinkApprovalResponse response =
                deviceLinkService.approveRequest(
                        primaryUser,
                        primarySessionId,
                        request.pairingCode()
                );

        return ResponseEntity.ok(response);
    }

    // Phone B: Check whether its request was approved.
    @PostMapping("/poll")
    public ResponseEntity<DeviceLinkPollResponse> pollRequest(
            @RequestBody DeviceLinkPollRequest request
    ) {
        DeviceLinkPollResponse response =
                deviceLinkService.pollRequest(request.pollToken());

        return ResponseEntity.ok(response);
    }
}
