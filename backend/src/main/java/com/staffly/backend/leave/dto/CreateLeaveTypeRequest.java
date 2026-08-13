package com.staffly.backend.leave.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLeaveTypeRequest(
        @NotBlank String nombre,
        @NotNull Boolean esPaga,
        @Min(1) Integer cupoAnual) {
}
