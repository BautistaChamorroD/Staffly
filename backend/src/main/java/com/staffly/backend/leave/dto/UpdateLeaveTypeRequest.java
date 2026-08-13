package com.staffly.backend.leave.dto;

import jakarta.validation.constraints.Min;

public record UpdateLeaveTypeRequest(
        String nombre,
        Boolean esPaga,
        @Min(1) Integer cupoAnual) {
}
