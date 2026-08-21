package com.staffly.backend.report;

import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.holiday.HolidayRepository;
import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.PayrollConfigRepository;
import com.staffly.backend.payroll.strategy.HoursBreakdown;
import com.staffly.backend.payroll.strategy.ScheduleHoursBreakdownCalculator;
import com.staffly.backend.payroll.strategy.ScheduleHoursBreakdownCalculator.ClassifiedSchedule;
import com.staffly.backend.report.dto.HoursWorkedRow;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.ScheduleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-22: horas trabajadas por empleado/sucursal/periodo, calculadas on-demand
 * sobre turnos CUMPLIDO.
 *
 * <p>La clasificacion normal/extra/feriado usa la misma fuente de verdad que
 * la liquidacion. El reporte sigue mostrando filas por empleado+sucursal, pero
 * el umbral de hora extra se evalua por empleado y por dia/semana, sin regalar
 * un umbral nuevo por cada sucursal.
 */
@Service
public class HoursWorkedReportService {

    private final ScheduleRepository      scheduleRepository;
    private final HolidayRepository       holidayRepository;
    private final PayrollConfigRepository configRepository;

    public HoursWorkedReportService(ScheduleRepository scheduleRepository,
                                    HolidayRepository holidayRepository,
                                    PayrollConfigRepository configRepository) {
        this.scheduleRepository = scheduleRepository;
        this.holidayRepository = holidayRepository;
        this.configRepository = configRepository;
    }

    @Transactional(readOnly = true)
    public List<HoursWorkedRow> generate(UUID companyId, UUID branchIdFilter, LocalDate desde, LocalDate hasta) {
        PayrollConfig config = configRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException(
                        "La empresa no tiene configuracion de liquidacion; configurala antes de consultar el reporte"));

        LocalDateTime desdeInicio = desde != null ? desde.atStartOfDay() : null;
        LocalDateTime hastaFin = hasta != null ? hasta.atTime(23, 59, 59) : null;

        List<LocalDate> holidays = (desde != null && hasta != null)
                ? holidayRepository.findApplicableFechasInRange(companyId, desde, hasta)
                : List.of();

        List<Schedule> schedules = scheduleRepository
                .findCumplidosForReport(companyId, null, desdeInicio, hastaFin);
        List<ClassifiedSchedule> classifiedSchedules = ScheduleHoursBreakdownCalculator
                .classify(schedules, holidays, config);

        Map<String, List<ClassifiedSchedule>> porEmpleadoYSucursal = new LinkedHashMap<>();
        for (ClassifiedSchedule classified : classifiedSchedules) {
            Schedule schedule = classified.schedule();
            if (branchIdFilter != null && !schedule.getBranch().getId().equals(branchIdFilter)) {
                continue;
            }

            String key = schedule.getEmployee().getId() + "|" + schedule.getBranch().getId();
            porEmpleadoYSucursal.computeIfAbsent(key, k -> new ArrayList<>()).add(classified);
        }

        List<HoursWorkedRow> rows = new ArrayList<>();
        for (List<ClassifiedSchedule> grupo : porEmpleadoYSucursal.values()) {
            rows.add(calcularFila(grupo));
        }
        return rows;
    }

    private HoursWorkedRow calcularFila(List<ClassifiedSchedule> turnos) {
        Schedule first = turnos.get(0).schedule();
        BigDecimal totalNormal = BigDecimal.ZERO;
        BigDecimal totalExtra = BigDecimal.ZERO;
        BigDecimal totalFeriado = BigDecimal.ZERO;

        for (ClassifiedSchedule classified : turnos) {
            HoursBreakdown bd = classified.breakdown();
            totalNormal = totalNormal.add(bd.normalHours());
            totalExtra = totalExtra.add(bd.overtimeHours());
            totalFeriado = totalFeriado.add(bd.holidayHours());
        }

        BigDecimal total = totalNormal.add(totalExtra).add(totalFeriado);

        return new HoursWorkedRow(
                first.getEmployee().getId(),
                first.getEmployee().getNombre(),
                first.getEmployee().getApellido(),
                first.getBranch().getId(),
                first.getBranch().getNombre(),
                totalNormal.setScale(2, RoundingMode.HALF_UP),
                totalExtra.setScale(2, RoundingMode.HALF_UP),
                totalFeriado.setScale(2, RoundingMode.HALF_UP),
                total.setScale(2, RoundingMode.HALF_UP)
        );
    }
}
