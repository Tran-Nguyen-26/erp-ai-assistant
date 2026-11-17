package vn.edu.uet.erpai.auth.service;

public record LoginResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {}
