package com.staffly.backend.payslip.builder;

import com.staffly.backend.payroll.decorator.DeduccionLinea;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resultado inmutable del cálculo de un recibo de sueldo.
 * No persiste nada — es la salida del {@link PayslipBuilder}.
 */
public record PayslipCalculation(
        UUID employeeId,
        UUID payrollPeriodId,

        BigDecimal sueldoBase,
        BigDecimal valorHoraBase,

        BigDecimal horasNormales,
        BigDecimal horasExtra,
        BigDecimal horasFeriado,

        BigDecimal montoHorasNormales,
        BigDecimal montoHorasExtra,
        BigDecimal montoHorasFeriado,

        BigDecimal brutoHoras,
        BigDecimal ajusteLicencias,
        BigDecimal brutoCalculado,

        List<DeduccionLinea> deducciones,
        BigDecimal totalDeducciones,

        List<UUID> adelantosAplicados,
        BigDecimal totalAdelantos,

        BigDecimal netoFinal
) {}
