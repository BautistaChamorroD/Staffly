package com.staffly.backend.payroll.strategy;

import java.time.LocalDate;
import java.util.List;

/** Selecciona en runtime la Strategy correcta para un turno dado. */
public final class HoursCalculationStrategySelector {

    private HoursCalculationStrategySelector() {}

    /**
     * @param shiftDate fecha de inicio del turno
     * @param holidays  lista de fechas de feriado aplicables a la empresa/sucursal
     */
    public static HoursCalculationStrategy select(LocalDate shiftDate, List<LocalDate> holidays) {
        if (holidays != null && holidays.contains(shiftDate)) {
            return new HolidayStrategy();
        }
        return new OvertimeStrategy();
    }
}
