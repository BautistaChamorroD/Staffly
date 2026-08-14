package com.staffly.backend.payroll.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreatePayrollPeriodRequest(
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin
) {}
