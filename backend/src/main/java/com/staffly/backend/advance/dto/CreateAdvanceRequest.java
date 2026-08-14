package com.staffly.backend.advance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAdvanceRequest(
        UUID employeeId,
        @NotNull LocalDate fecha,
        @NotNull @DecimalMin("0.01") BigDecimal monto,
        @Size(max = 500) String motivo
) {}
