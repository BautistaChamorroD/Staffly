package com.staffly.backend.payroll.strategy;

import com.staffly.backend.payroll.PayrollConfig;

import java.math.BigDecimal;

/**
 * Separa las horas del turno en normales y extra usando el umbral de {@link PayrollConfig}.
 *
 * <p>Semántica: el umbral es <em>acumulado por período</em>, no por turno.
 * Se pasa {@code hoursAlreadyCounted} para saber cuánto margen queda antes de
 * superar el umbral. Ejemplos con umbral = 8:
 * <ul>
 *   <li>counted=0, shift=7 → 7 normales, 0 extra</li>
 *   <li>counted=0, shift=8 → 8 normales, 0 extra  (exactamente en el umbral)</li>
 *   <li>counted=0, shift=10 → 8 normales, 2 extra</li>
 *   <li>counted=6, shift=4 → 2 normales, 2 extra</li>
 *   <li>counted=9, shift=4 → 0 normales, 4 extra  (ya superó el umbral)</li>
 * </ul>
 */
public class OvertimeStrategy implements HoursCalculationStrategy {

    @Override
    public HoursBreakdown calculate(BigDecimal shiftHours, BigDecimal hoursAlreadyCounted, PayrollConfig config) {
        BigDecimal threshold = config.getUmbralHorasExtra();

        if (hoursAlreadyCounted.compareTo(threshold) >= 0) {
            return HoursBreakdown.allOvertime(shiftHours);
        }

        BigDecimal remainingNormal = threshold.subtract(hoursAlreadyCounted);
        if (shiftHours.compareTo(remainingNormal) <= 0) {
            return HoursBreakdown.allNormal(shiftHours);
        }

        BigDecimal normalPart = remainingNormal;
        BigDecimal overtimePart = shiftHours.subtract(remainingNormal);
        return new HoursBreakdown(normalPart, overtimePart, BigDecimal.ZERO);
    }
}
