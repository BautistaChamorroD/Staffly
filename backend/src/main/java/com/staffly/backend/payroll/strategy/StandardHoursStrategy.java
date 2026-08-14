package com.staffly.backend.payroll.strategy;

import com.staffly.backend.payroll.PayrollConfig;

import java.math.BigDecimal;

/** Todas las horas del turno se computan como normales, sin chequeo de umbral. */
public class StandardHoursStrategy implements HoursCalculationStrategy {

    @Override
    public HoursBreakdown calculate(BigDecimal shiftHours, BigDecimal hoursAlreadyCounted, PayrollConfig config) {
        return HoursBreakdown.allNormal(shiftHours);
    }
}
