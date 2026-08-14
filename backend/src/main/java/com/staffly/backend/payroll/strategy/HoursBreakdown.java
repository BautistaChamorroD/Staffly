package com.staffly.backend.payroll.strategy;

import java.math.BigDecimal;

/**
 * Resultado de categorizar las horas de un turno: qué porción es normal,
 * cuál supera el umbral de hora extra y cuál cae en un día feriado.
 * Las tres son mutuamente excluyentes por turno.
 */
public record HoursBreakdown(
        BigDecimal normalHours,
        BigDecimal overtimeHours,
        BigDecimal holidayHours
) {
    public BigDecimal total() {
        return normalHours.add(overtimeHours).add(holidayHours);
    }

    public static HoursBreakdown allNormal(BigDecimal hours) {
        return new HoursBreakdown(hours, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static HoursBreakdown allOvertime(BigDecimal hours) {
        return new HoursBreakdown(BigDecimal.ZERO, hours, BigDecimal.ZERO);
    }

    public static HoursBreakdown allHoliday(BigDecimal hours) {
        return new HoursBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, hours);
    }
}
