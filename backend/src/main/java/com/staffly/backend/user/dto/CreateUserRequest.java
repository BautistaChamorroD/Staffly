package com.staffly.backend.user.dto;

import com.staffly.backend.user.RolUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotNull RolUsuario rol,
        UUID employeeId,
        List<UUID> branchIds) {
}
