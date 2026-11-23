package com.cooking.app.dto;

public final class AuthDtos {
    private AuthDtos() {}

    public record Register(String fullName, String email, String phone, String password, String confirmation) {}
    public record Login(String identifier, String password) {}
    public record PasswordChange(String currentPassword, String password, String confirmation) {}
    public record ProfileUpdate(String fullName, String email, String phone, String bio, String avatarUrl) {}
    public record Forgot(String email) {}
    public record Reset(String token, String password, String confirmation) {}
    public record Session(String token) {}
    public record Message(String message) {}
}
