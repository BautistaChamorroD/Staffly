package com.staffly.backend.payroll.strategy;

import com.staffly.backend.payroll.PayrollConfig;

import java.math.BigDecimal;

/**
 * Todas las horas del turno se computan como horas de feriado.
 * El multiplicador de feriado de {@link PayrollConfig} se aplica sobre el
 * total — no se chequea umbral de hora extra en días feriados.
 */
public class HolidayStrategy implements HoursCalculationStrategy {

    @Override
    public HoursBreakdown calculate(BigDecimal shiftHours, BigDecimal hoursAlreadyCounted, PayrollConfig config) {
        return HoursBreakdown.allHoliday(shiftHours);
    }
}
