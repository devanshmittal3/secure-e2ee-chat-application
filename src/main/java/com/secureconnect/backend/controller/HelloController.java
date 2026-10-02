package com.secureconnect.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public String hello() {
        return "SecureConnect Backend is running!";
    }

    @GetMapping("/api/protected")
    public String protectedEndpoint() {
        return "You are authenticated!";
    }
}