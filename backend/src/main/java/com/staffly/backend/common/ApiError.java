package com.staffly.backend.common;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        Object details,
        Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, Instant.now());
    }
}
