package com.staffly.backend.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record HoursWorkedRow(
        UUID employeeId,
        String employeeNombre,
        String employeeApellido,
        UUID branchId,
        String branchNombre,
        BigDecimal horasNormales,
        BigDecimal horasExtra,
        BigDecimal horasFeriado,
        BigDecimal totalHoras
) {}
