package com.staffly.backend.payslip.builder;

import com.staffly.backend.advance.Advance;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.leave.LeaveRequest;
import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payroll.Periodicidad;
import com.staffly.backend.payroll.TipoUmbral;
import com.staffly.backend.payroll.decorator.DeduccionLinea;
import com.staffly.backend.payroll.decorator.DiscountChainBuilder;
import com.staffly.backend.payroll.decorator.SalaryComponent;
import com.staffly.backend.payroll.strategy.HolidayStrategy;
import com.staffly.backend.payroll.strategy.HoursBreakdown;
import com.staffly.backend.payroll.strategy.HoursCalculationStrategySelector;
import com.staffly.backend.payroll.strategy.OvertimeStrategy;
import com.staffly.backend.schedule.EstadoTurno;
import com.staffly.backend.schedule.Schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builder para calcular el detalle de un recibo de sueldo.
 *
 * <p>Orden de pasos (crítico — no invertir):
 * <ol>
 *   <li>Suma horas CUMPLIDO del período aplicando Strategy hora extra / feriado</li>
 *   <li>Aplica impacto de licencias aprobadas (+ si paga, − si no paga)</li>
 *   <li>Aplica conceptos de descuento de PayrollConfig (Decorator)</li>
 *   <li>Descuenta adelantos PENDIENTE</li>
 * </ol>
 */
public final class PayslipBuilder {

    private static final BigDecimal HORAS_NOMINALES_MENSUAL    = BigDecimal.valueOf(200);
    private static final BigDecimal HORAS_NOMINALES_QUINCENAL  = BigDecimal.valueOf(100);
    private static final BigDecimal HORAS_POR_DIA              = BigDecimal.valueOf(8);

    private Employee      employee;
    private PayrollPeriod period;
    private PayrollConfig config;
    private List<Schedule>     schedules     = List.of();
    private List<LocalDate>    holidays      = List.of();
    private List<LeaveRequest> leaveRequests = List.of();
    private List<Advance>      advances      = List.of();

    public PayslipBuilder withEmployee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public PayslipBuilder withPeriod(PayrollPeriod period) {
        this.period = period;
        return this;
    }

    public PayslipBuilder withConfig(PayrollConfig config) {
        this.config = config;
        return this;
    }

    public PayslipBuilder withSchedules(List<Schedule> schedules) {
        this.schedules = schedules != null ? schedules : List.of();
        return this;
    }

    public PayslipBuilder withHolidays(List<LocalDate> holidays) {
        this.holidays = holidays != null ? holidays : List.of();
        return this;
    }

    public PayslipBuilder withLeaveRequests(List<LeaveRequest> leaveRequests) {
        this.leaveRequests = leaveRequests != null ? leaveRequests : List.of();
        return this;
    }

    public PayslipBuilder withAdvances(List<Advance> advances) {
        this.advances = advances != null ? advances : List.of();
        return this;
    }

    public PayslipCalculation build() {
        if (employee == null || period == null || config == null) {
            throw new IllegalStateException("employee, period y config son requeridos");
        }

        BigDecimal sueldoBase  = employee.getSueldoBase();
        BigDecimal horasNom    = horasNominales(config.getPeriodicidad());
        BigDecimal valorHora   = sueldoBase.divide(horasNom, 6, RoundingMode.HALF_UP);

        // ── Paso 1: horas por turnos CUMPLIDO ────────────────────────────────
        // El umbral de hora extra se agrupa por día o por semana (RF-16, config.tipoUmbral)
        // — nunca acumulado sobre todo el período, o casi todas las horas del segundo
        // día en adelante terminarían contando como extra (AUD-24 / issue #129).
        BigDecimal totalNormal   = BigDecimal.ZERO;
        BigDecimal totalExtra    = BigDecimal.ZERO;
        BigDecimal totalFeriado  = BigDecimal.ZERO;

        List<Schedule> cumplidos = schedules.stream()
                .filter(s -> s.getEstado() == EstadoTurno.CUMPLIDO)
                .toList();

        Map<Object, BigDecimal> horasPorGrupo = new LinkedHashMap<>();
        for (Schedule s : cumplidos) {
            BigDecimal shiftHours = horasDelTurno(s);
            LocalDate  shiftDate  = s.getFechaHoraInicio().toLocalDate();

            if (HoursCalculationStrategySelector.select(shiftDate, holidays) instanceof HolidayStrategy) {
                HoursBreakdown bd = new HolidayStrategy().calculate(shiftHours, BigDecimal.ZERO, config);
                totalFeriado = totalFeriado.add(bd.holidayHours());
            } else {
                Object grupo = umbralGroupKey(shiftDate, config.getTipoUmbral());
                horasPorGrupo.merge(grupo, shiftHours, BigDecimal::add);
            }
        }

        for (BigDecimal horasGrupo : horasPorGrupo.values()) {
            HoursBreakdown bd = new OvertimeStrategy().calculate(horasGrupo, BigDecimal.ZERO, config);
            totalNormal = totalNormal.add(bd.normalHours());
            totalExtra  = totalExtra.add(bd.overtimeHours());
        }

        BigDecimal montoHorasNormales = totalNormal.multiply(valorHora)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoHorasExtra = totalExtra.multiply(valorHora)
                .multiply(config.getMultiplicadorHoraExtra())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoHorasFeriado = totalFeriado.multiply(valorHora)
                .multiply(config.getMultiplicadorFeriado())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal brutoHoras = montoHorasNormales.add(montoHorasExtra).add(montoHorasFeriado);

        // ── Paso 2: licencias aprobadas ───────────────────────────────────────
        BigDecimal ajusteLicencias = BigDecimal.ZERO;
        BigDecimal valorDia        = valorHora.multiply(HORAS_POR_DIA);

        for (LeaveRequest lr : leaveRequests) {
            long dias = diasSolapados(lr.getFechaInicio(), lr.getFechaFin(),
                    period.getFechaInicio(), period.getFechaFin());
            if (dias <= 0) continue;

            BigDecimal impacto = valorDia.multiply(BigDecimal.valueOf(dias));
            ajusteLicencias = lr.getLeaveType().isEsPaga()
                    ? ajusteLicencias.add(impacto)
                    : ajusteLicencias.subtract(impacto);
        }
        ajusteLicencias = ajusteLicencias.setScale(2, RoundingMode.HALF_UP);

        BigDecimal brutoCalculado = brutoHoras.add(ajusteLicencias)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        // ── Paso 3: descuentos por conceptos de PayrollConfig (Decorator) ────
        SalaryComponent chain = DiscountChainBuilder.build(brutoCalculado,
                config.getConceptosDescuento());

        List<DeduccionLinea> deducciones   = Collections.unmodifiableList(chain.getDeductions());
        BigDecimal totalDeducciones        = deducciones.stream()
                .map(DeduccionLinea::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netoTrasDescuentos = chain.getNetAmount().setScale(2, RoundingMode.HALF_UP);

        // ── Paso 4: adelantos ─────────────────────────────────────────────────
        List<UUID>   adelantosIds  = new ArrayList<>();
        BigDecimal   totalAdelantos = BigDecimal.ZERO;

        for (Advance a : advances) {
            adelantosIds.add(a.getId());
            totalAdelantos = totalAdelantos.add(a.getMonto());
        }
        totalAdelantos = totalAdelantos.setScale(2, RoundingMode.HALF_UP);

        BigDecimal netoFinal = netoTrasDescuentos.subtract(totalAdelantos)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new PayslipCalculation(
                employee.getId(),
                period.getId(),
                sueldoBase,
                valorHora.setScale(6, RoundingMode.HALF_UP),
                totalNormal.setScale(2, RoundingMode.HALF_UP),
                totalExtra.setScale(2, RoundingMode.HALF_UP),
                totalFeriado.setScale(2, RoundingMode.HALF_UP),
                montoHorasNormales,
                montoHorasExtra,
                montoHorasFeriado,
                brutoHoras,
                ajusteLicencias,
                brutoCalculado,
                deducciones,
                totalDeducciones,
                Collections.unmodifiableList(adelantosIds),
                totalAdelantos,
                netoFinal
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal horasNominales(Periodicidad periodicidad) {
        return periodicidad == Periodicidad.QUINCENAL
                ? HORAS_NOMINALES_QUINCENAL
                : HORAS_NOMINALES_MENSUAL;
    }

    private static BigDecimal horasDelTurno(Schedule s) {
        long minutos = ChronoUnit.MINUTES.between(s.getFechaHoraInicio(), s.getFechaHoraFin());
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }

    /** Clave de agrupación para el umbral de hora extra: por día calendario o por semana ISO. */
    private static Object umbralGroupKey(LocalDate shiftDate, TipoUmbral tipoUmbral) {
        if (tipoUmbral == TipoUmbral.SEMANAL) {
            WeekFields iso = WeekFields.ISO;
            return shiftDate.get(iso.weekBasedYear()) + "-W" + shiftDate.get(iso.weekOfWeekBasedYear());
        }
        return shiftDate;
    }

    private static long diasSolapados(LocalDate ini, LocalDate fin,
                                      LocalDate periodoIni, LocalDate periodoFin) {
        LocalDate solapIni = ini.isAfter(periodoIni) ? ini : periodoIni;
        LocalDate solapFin = fin.isBefore(periodoFin) ? fin : periodoFin;
        if (solapFin.isBefore(solapIni)) return 0;
        return ChronoUnit.DAYS.between(solapIni, solapFin) + 1;
    }
}
