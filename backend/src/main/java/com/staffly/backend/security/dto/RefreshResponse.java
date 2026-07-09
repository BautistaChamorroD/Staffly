package com.staffly.backend.security.dto;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn) {
}
