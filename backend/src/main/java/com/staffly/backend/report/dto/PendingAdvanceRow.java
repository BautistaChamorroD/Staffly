package com.staffly.backend.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PendingAdvanceRow(
        UUID id,
        UUID employeeId,
        String employeeNombre,
        String employeeApellido,
        BigDecimal monto,
        LocalDate fecha,
        String motivo
) {}
