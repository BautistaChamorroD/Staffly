package com.staffly.backend.leave.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateLeaveRequestRequest(
        UUID employeeId,
        @NotNull UUID leaveTypeId,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin,
        String motivo) {
}
