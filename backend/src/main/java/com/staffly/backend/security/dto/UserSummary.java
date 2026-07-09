package com.staffly.backend.security.dto;

import com.staffly.backend.security.Rol;

import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        Rol rol,
        boolean debeCambiarPassword) {
}
