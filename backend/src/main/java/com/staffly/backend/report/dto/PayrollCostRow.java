package com.staffly.backend.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollCostRow(
        UUID branchId,
        String branchNombre,
        int cantidadEmpleados,
        BigDecimal totalBruto,
        BigDecimal totalDeducciones,
        BigDecimal totalNeto
) {}
