package com.staffly.backend.security.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserSummary user) {
}
