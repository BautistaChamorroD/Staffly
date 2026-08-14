package com.staffly.backend.payroll.strategy;

import com.staffly.backend.payroll.PayrollConfig;

import java.math.BigDecimal;

/**
 * Strategy para categorizar las horas de un turno en normales, extra o feriado.
 *
 * @param shiftHours          horas totales del turno (duración)
 * @param hoursAlreadyCounted horas ya contabilizadas para este empleado en el período
 *                            antes de procesar este turno — usado para el cálculo de umbral de hora extra
 * @param config              configuración de nómina con umbrales y multiplicadores
 */
public interface HoursCalculationStrategy {
    HoursBreakdown calculate(BigDecimal shiftHours, BigDecimal hoursAlreadyCounted, PayrollConfig config);
}
