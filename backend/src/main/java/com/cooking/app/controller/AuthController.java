package com.cooking.app.controller;

import com.cooking.app.dto.AuthDtos.*;
import com.cooking.app.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/auth/register")
    Message register(@RequestBody Register input) { return auth.register(input); }

    @PostMapping("/auth/login")
    Session login(@RequestBody Login input) { return auth.login(input); }

    @PostMapping("/auth/forgot-password")
    Message forgot(@RequestBody Forgot input) { return auth.forgot(input); }

    @PostMapping("/auth/reset-password")
    Message reset(@RequestBody Reset input) { return auth.reset(input); }
}
