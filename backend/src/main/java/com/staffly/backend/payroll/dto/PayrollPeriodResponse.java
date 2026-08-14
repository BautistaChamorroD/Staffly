package com.staffly.backend.payroll.dto;

import com.staffly.backend.payroll.EstadoPeriodo;
import com.staffly.backend.payroll.PayrollPeriod;

import java.time.LocalDate;
import java.util.UUID;

public record PayrollPeriodResponse(
        UUID id,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        EstadoPeriodo estado,
        LocalDate fechaCierre
) {
    public static PayrollPeriodResponse from(PayrollPeriod period) {
        return new PayrollPeriodResponse(
                period.getId(),
                period.getFechaInicio(),
                period.getFechaFin(),
                period.getEstado(),
                period.getFechaCierre()
        );
    }
}
