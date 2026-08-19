package com.staffly.backend.report;

import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.holiday.HolidayRepository;
import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.PayrollConfigRepository;
import com.staffly.backend.payroll.TipoUmbral;
import com.staffly.backend.payroll.strategy.HolidayStrategy;
import com.staffly.backend.payroll.strategy.HoursBreakdown;
import com.staffly.backend.payroll.strategy.HoursCalculationStrategySelector;
import com.staffly.backend.payroll.strategy.OvertimeStrategy;
import com.staffly.backend.report.dto.HoursWorkedRow;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.ScheduleRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-22: horas trabajadas por empleado/sucursal/período, calculadas on-demand
 * sobre {@link Schedule} — no depende de que exista un {@code PayrollPeriod}
 * cerrado (a diferencia de {@code PayslipBuilder}, que sí).
 *
 * <p>El umbral de hora extra se agrupa por empleado+sucursal (no cruza
 * sucursales) usando el mismo criterio día/semana de {@link PayrollConfig} que
 * ya usa el cierre de nómina — ver {@code PayslipBuilder} para el mismo
 * algoritmo aplicado al cálculo monetario de un período.
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
                        "La empresa no tiene configuración de liquidación — configurala antes de consultar el reporte"));

        LocalDateTime desdeInicio = desde != null ? desde.atStartOfDay() : null;
        LocalDateTime hastaFin = hasta != null ? hasta.atTime(23, 59, 59) : null;

        List<LocalDate> holidays = (desde != null && hasta != null)
                ? holidayRepository.findApplicableFechasInRange(companyId, desde, hasta)
                : List.of();

        List<Schedule> schedules = scheduleRepository
                .findCumplidosForReport(companyId, branchIdFilter, desdeInicio, hastaFin);

        // agrupar por (empleado, sucursal) — cada combinación es una fila del reporte.
        // El umbral de hora extra se calcula dentro de cada grupo, es decir por
        // sucursal — un empleado en dos sucursales el mismo día tiene el umbral
        // aplicado de forma independiente en cada una (ver Javadoc de la clase).
        Map<String, List<Schedule>> porEmpleadoYSucursal = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            String key = s.getEmployee().getId() + "|" + s.getBranch().getId();
            porEmpleadoYSucursal.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<HoursWorkedRow> rows = new ArrayList<>();
        for (List<Schedule> grupo : porEmpleadoYSucursal.values()) {
            rows.add(calcularFila(grupo, config, holidays));
        }
        return rows;
    }

    private HoursWorkedRow calcularFila(List<Schedule> turnos, PayrollConfig config, List<LocalDate> holidays) {
        Schedule first = turnos.get(0);
        BigDecimal totalNormal = BigDecimal.ZERO;
        BigDecimal totalExtra = BigDecimal.ZERO;
        BigDecimal totalFeriado = BigDecimal.ZERO;

        Map<Object, BigDecimal> horasPorGrupo = new LinkedHashMap<>();
        for (Schedule s : turnos) {
            LocalDate shiftDate = s.getFechaHoraInicio().toLocalDate();
            BigDecimal shiftHours = horasDelTurno(s);

            if (HoursCalculationStrategySelector.select(shiftDate, holidays) instanceof HolidayStrategy) {
                HoursBreakdown bd = new HolidayStrategy().calculate(shiftHours, BigDecimal.ZERO, config);
                totalFeriado = totalFeriado.add(bd.holidayHours());
            } else {
                Object key = umbralGroupKey(shiftDate, config.getTipoUmbral());
                horasPorGrupo.merge(key, shiftHours, BigDecimal::add);
            }
        }

        for (BigDecimal horasGrupo : horasPorGrupo.values()) {
            HoursBreakdown bd = new OvertimeStrategy().calculate(horasGrupo, BigDecimal.ZERO, config);
            totalNormal = totalNormal.add(bd.normalHours());
            totalExtra = totalExtra.add(bd.overtimeHours());
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

    private static BigDecimal horasDelTurno(Schedule s) {
        long minutos = ChronoUnit.MINUTES.between(s.getFechaHoraInicio(), s.getFechaHoraFin());
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }

    private static Object umbralGroupKey(LocalDate shiftDate, TipoUmbral tipoUmbral) {
        if (tipoUmbral == TipoUmbral.SEMANAL) {
            WeekFields iso = WeekFields.ISO;
            return shiftDate.get(iso.weekBasedYear()) + "-W" + shiftDate.get(iso.weekOfWeekBasedYear());
        }
        return shiftDate;
    }
}
