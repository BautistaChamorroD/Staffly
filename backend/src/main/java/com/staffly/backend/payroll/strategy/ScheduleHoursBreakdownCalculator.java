package com.staffly.backend.payroll.strategy;

import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.TipoUmbral;
import com.staffly.backend.schedule.EstadoTurno;
import com.staffly.backend.schedule.Schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fuente comun para clasificar turnos CUMPLIDO en horas normales, extra y feriado.
 *
 * <p>El umbral se evalua por empleado y por dia/semana, igual que en liquidacion.
 * Si un empleado trabaja en mas de una sucursal, no recibe un umbral nuevo por
 * cada sucursal.
 */
public final class ScheduleHoursBreakdownCalculator {

    private ScheduleHoursBreakdownCalculator() {}

    public record ClassifiedSchedule(Schedule schedule, HoursBreakdown breakdown) {}

    public static List<ClassifiedSchedule> classify(List<Schedule> schedules,
                                                    List<LocalDate> holidays,
                                                    PayrollConfig config) {
        if (schedules == null || schedules.isEmpty()) {
            return List.of();
        }

        List<Schedule> cumplidos = schedules.stream()
                .filter(s -> s.getEstado() == EstadoTurno.CUMPLIDO)
                .sorted(Comparator
                        .comparing((Schedule s) -> s.getEmployee().getId(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Schedule::getFechaHoraInicio)
                        .thenComparing(Schedule::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<Object, BigDecimal> horasContadasPorGrupo = new LinkedHashMap<>();
        return cumplidos.stream()
                .map(schedule -> {
                    BigDecimal shiftHours = hoursOf(schedule);
                    LocalDate shiftDate = schedule.getFechaHoraInicio().toLocalDate();

                    if (HoursCalculationStrategySelector.select(shiftDate, holidays) instanceof HolidayStrategy) {
                        return new ClassifiedSchedule(
                                schedule,
                                new HolidayStrategy().calculate(shiftHours, BigDecimal.ZERO, config));
                    }

                    Object groupKey = groupKey(schedule.getEmployee().getId(), shiftDate, config.getTipoUmbral());
                    BigDecimal hoursAlreadyCounted = horasContadasPorGrupo.getOrDefault(groupKey, BigDecimal.ZERO);
                    HoursBreakdown breakdown = new OvertimeStrategy()
                            .calculate(shiftHours, hoursAlreadyCounted, config);
                    horasContadasPorGrupo.merge(groupKey, shiftHours, BigDecimal::add);
                    return new ClassifiedSchedule(schedule, breakdown);
                })
                .toList();
    }

    public static HoursBreakdown summarize(List<Schedule> schedules,
                                           List<LocalDate> holidays,
                                           PayrollConfig config) {
        BigDecimal totalNormal = BigDecimal.ZERO;
        BigDecimal totalExtra = BigDecimal.ZERO;
        BigDecimal totalFeriado = BigDecimal.ZERO;

        for (ClassifiedSchedule classified : classify(schedules, holidays, config)) {
            totalNormal = totalNormal.add(classified.breakdown().normalHours());
            totalExtra = totalExtra.add(classified.breakdown().overtimeHours());
            totalFeriado = totalFeriado.add(classified.breakdown().holidayHours());
        }

        return new HoursBreakdown(
                totalNormal.setScale(2, RoundingMode.HALF_UP),
                totalExtra.setScale(2, RoundingMode.HALF_UP),
                totalFeriado.setScale(2, RoundingMode.HALF_UP));
    }

    public static BigDecimal hoursOf(Schedule schedule) {
        long minutos = ChronoUnit.MINUTES.between(
                schedule.getFechaHoraInicio(), schedule.getFechaHoraFin());
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }

    private static Object groupKey(UUID employeeId, LocalDate shiftDate, TipoUmbral tipoUmbral) {
        if (tipoUmbral == TipoUmbral.SEMANAL) {
            WeekFields iso = WeekFields.ISO;
            return employeeId + "|" + shiftDate.get(iso.weekBasedYear()) + "-W"
                    + shiftDate.get(iso.weekOfWeekBasedYear());
        }
        return employeeId + "|" + shiftDate;
    }
}
