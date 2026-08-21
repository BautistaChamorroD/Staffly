package com.staffly.backend.payslip.builder;

import com.staffly.backend.advance.Advance;
import com.staffly.backend.advance.EstadoAdelanto;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
import com.staffly.backend.leave.EstadoLicencia;
import com.staffly.backend.leave.LeaveRequest;
import com.staffly.backend.leave.LeaveType;
import com.staffly.backend.payroll.ConceptoDescuento;
import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payroll.Periodicidad;
import com.staffly.backend.payroll.TipoConcepto;
import com.staffly.backend.payroll.TipoUmbral;
import com.staffly.backend.schedule.EstadoTurno;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.TipoTurno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PayslipBuilderTest {

    // Período: agosto 2026 (mes completo)
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate PERIOD_END   = LocalDate.of(2026, 8, 31);

    private Employee     employee;
    private PayrollPeriod period;
    private PayrollConfig config;
    private Branch        branch;

    @BeforeEach
    void setUp() {
        branch   = new Branch();

        employee = new Employee();
        employee.setSueldoBase(BigDecimal.valueOf(200_000));
        employee.setNombre("Juan"); employee.setApellido("Pérez");
        employee.setDocumento("12345678");
        employee.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        employee.setFechaIngreso(LocalDate.of(2024, 1, 1));
        employee.setTipoContrato(TipoContrato.POR_HORA);
        employee.setCategoria("General");
        employee.setEstadoLaboral(EstadoLaboral.ACTIVO);
        employee.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);

        period = new PayrollPeriod();
        period.setFechaInicio(PERIOD_START);
        period.setFechaFin(PERIOD_END);

        config = new PayrollConfig();
        config.setUmbralHorasExtra(BigDecimal.valueOf(8));
        config.setMultiplicadorHoraExtra(BigDecimal.valueOf(1.5));
        config.setMultiplicadorFeriado(BigDecimal.valueOf(2.0));
        config.setPeriodicidad(Periodicidad.MENSUAL);
        config.setTipoUmbral(TipoUmbral.DIARIO);
        config.setConceptosDescuento(List.of());
    }

    // ── Paso 1: horas de turnos ───────────────────────────────────────────────

    @Test
    void empleadoSinHoras_netoEsCero() {
        PayslipCalculation result = builder().build();

        assertEquals(BigDecimal.ZERO.setScale(2), result.horasNormales());
        assertEquals(BigDecimal.ZERO.setScale(2), result.horasExtra());
        assertEquals(BigDecimal.ZERO.setScale(2), result.horasFeriado());
        assertEquals(BigDecimal.ZERO.setScale(2), result.brutoCalculado());
        assertEquals(BigDecimal.ZERO.setScale(2), result.netoFinal());
    }

    @Test
    void jornadaCompletaSinTurnos_cobraSueldoBaseDelPeriodo() {
        employee.setTipoContrato(TipoContrato.JORNADA_COMPLETA);

        PayslipCalculation result = builder().build();

        assertEquals(bd("0.00"), result.horasNormales());
        assertEquals(bd("200000.00"), result.brutoHoras());
        assertEquals(bd("200000.00"), result.brutoCalculado());
        assertEquals(bd("200000.00"), result.netoFinal());
    }

    @Test
    void jornadaCompletaConLicenciaPaga_noDuplicaSueldoBase() {
        employee.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        LeaveRequest lr = leaveRequest(
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 8), true);

        PayslipCalculation result = builder().withLeaveRequests(List.of(lr)).build();

        assertEquals(bd("0.00"), result.ajusteLicencias());
        assertEquals(bd("200000.00"), result.brutoCalculado());
    }

    @Test
    void jornadaCompletaConLicenciaNoPaga_descuentaDiasDelSueldoBase() {
        employee.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        LeaveRequest lr = leaveRequest(
                LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 6), false);

        PayslipCalculation result = builder().withLeaveRequests(List.of(lr)).build();

        assertEquals(bd("-16000.00"), result.ajusteLicencias());
        assertEquals(bd("184000.00"), result.brutoCalculado());
    }

    @Test
    void turnoNormal_menorAlUmbral() {
        // 7h < umbral 8h → todo normal
        List<Schedule> schedules = List.of(shift(2026, 8, 4, 9, 0, 16, 0));
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("7.00"), result.horasNormales());
        assertEquals(bd("0.00"), result.horasExtra());
        assertEquals(bd("0.00"), result.horasFeriado());
        // bruto = 7h × (200000/200) = 7 × 1000 = 7000
        assertEquals(bd("7000.00"), result.brutoCalculado());
    }

    @Test
    void jornadaParcialMantieneCalculoPorHorasCumplidas() {
        employee.setTipoContrato(TipoContrato.JORNADA_PARCIAL);

        List<Schedule> schedules = List.of(shift(2026, 8, 4, 9, 0, 16, 0));
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("7.00"), result.horasNormales());
        assertEquals(bd("7000.00"), result.brutoCalculado());
    }

    @Test
    void horaExtraExactaAlUmbral_todoNormal() {
        // Exactamente 8h → sin hora extra
        List<Schedule> schedules = List.of(shift(2026, 8, 4, 9, 0, 17, 0));
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("8.00"), result.horasNormales());
        assertEquals(bd("0.00"), result.horasExtra());
        // bruto = 8 × 1000 = 8000
        assertEquals(bd("8000.00"), result.brutoCalculado());
    }

    @Test
    void periodicidadSemanal_usaCuarentaHorasNominales() {
        config.setPeriodicidad(Periodicidad.SEMANAL);

        List<Schedule> schedules = List.of(shift(2026, 8, 4, 9, 0, 17, 0));
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("5000.000000"), result.valorHoraBase());
        assertEquals(bd("8.00"), result.horasNormales());
        assertEquals(bd("40000.00"), result.brutoCalculado());
    }

    @Test
    void horaExtraSupeUmbral_split() {
        // 10h → 8 normales + 2 extra
        List<Schedule> schedules = List.of(shift(2026, 8, 4, 8, 0, 18, 0));
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("8.00"),  result.horasNormales());
        assertEquals(bd("2.00"),  result.horasExtra());
        // bruto = 8×1000 + 2×1000×1.5 = 8000 + 3000 = 11000
        assertEquals(bd("11000.00"), result.brutoCalculado());
    }

    @Test
    void turnoEnFeriado_todasHorasFeriado() {
        LocalDate christmas = LocalDate.of(2026, 8, 25); // simulamos feriado en período
        List<Schedule> schedules = List.of(shift(2026, 8, 25, 9, 0, 17, 0)); // 8h
        PayslipCalculation result = builder()
                .withSchedules(schedules)
                .withHolidays(List.of(christmas))
                .build();

        assertEquals(bd("0.00"), result.horasNormales());
        assertEquals(bd("0.00"), result.horasExtra());
        assertEquals(bd("8.00"), result.horasFeriado());
        // bruto = 8 × 1000 × 2.0 = 16000
        assertEquals(bd("16000.00"), result.brutoCalculado());
    }

    @Test
    void montosPorCategoriaDeHora_horaExtraYFeriadoSimultaneas() {
        // AUD-25 / issue #130: cada categoría de hora debe traer su propio monto,
        // no reusar sueldoBase/brutoCalculado como aproximación.
        LocalDate feriado = LocalDate.of(2026, 8, 25);
        List<Schedule> schedules = List.of(
                shift(2026, 8, 4, 8, 0, 18, 0),   // 10h en día normal → 8 normal + 2 extra
                shift(2026, 8, 25, 9, 0, 17, 0)   // 8h en feriado
        );
        PayslipCalculation result = builder()
                .withSchedules(schedules)
                .withHolidays(List.of(feriado))
                .build();

        // valorHora = 200000/200 = 1000
        assertEquals(bd("8000.00"), result.montoHorasNormales());   // 8 × 1000
        assertEquals(bd("3000.00"), result.montoHorasExtra());      // 2 × 1000 × 1.5
        assertEquals(bd("16000.00"), result.montoHorasFeriado());   // 8 × 1000 × 2.0
        // la suma de las 3 categorías debe coincidir con el bruto de horas
        assertEquals(result.brutoHoras(),
                result.montoHorasNormales().add(result.montoHorasExtra()).add(result.montoHorasFeriado()));
    }

    @Test
    void turnosFeriadoYNormal_combinados() {
        LocalDate feriado = LocalDate.of(2026, 8, 25);
        List<Schedule> schedules = List.of(
                shift(2026, 8, 4,  9, 0, 17, 0), // 8h normal
                shift(2026, 8, 25, 9, 0, 17, 0)  // 8h feriado
        );
        PayslipCalculation result = builder()
                .withSchedules(schedules)
                .withHolidays(List.of(feriado))
                .build();

        assertEquals(bd("8.00"), result.horasNormales());
        assertEquals(bd("0.00"), result.horasExtra());
        assertEquals(bd("8.00"), result.horasFeriado());
        // 8000 normal + 16000 feriado = 24000
        assertEquals(bd("24000.00"), result.brutoCalculado());
    }

    @Test
    void umbralDiario_cincoTurnosDeOchoHorasEnDiasDistintos_sinHoraExtra() {
        // AUD-24 / issue #129: el umbral es por día, no acumulado sobre todo el período.
        // 5 turnos de 8h en 5 días distintos de un período MENSUAL con umbralHorasExtra=8
        // deben dar 0h extra — antes del fix daban 8h normales + 32h extra.
        config.setTipoUmbral(TipoUmbral.DIARIO);
        List<Schedule> schedules = List.of(
                shift(2026, 8, 3, 9, 0, 17, 0),
                shift(2026, 8, 4, 9, 0, 17, 0),
                shift(2026, 8, 5, 9, 0, 17, 0),
                shift(2026, 8, 6, 9, 0, 17, 0),
                shift(2026, 8, 7, 9, 0, 17, 0)
        );
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("40.00"), result.horasNormales());
        assertEquals(bd("0.00"), result.horasExtra());
    }

    @Test
    void umbralDiario_turnoDeDiezHorasEnUnDia_dosHorasExtra() {
        config.setTipoUmbral(TipoUmbral.DIARIO);
        List<Schedule> schedules = List.of(
                shift(2026, 8, 3, 8, 0, 17, 0),  // 9h
                shift(2026, 8, 3, 17, 0, 18, 0)  // +1h el mismo día = 10h total
        );
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("8.00"), result.horasNormales());
        assertEquals(bd("2.00"), result.horasExtra());
    }

    @Test
    void umbralSemanal_turnosDeLaMismaSemanaSeAcumulan() {
        // umbral semanal de 8h (reutilizamos el mismo valor de config para simplificar
        // el escenario) — 5 turnos de 8h en la misma semana ISO deben acumular hasta
        // superar el umbral, a diferencia del caso DIARIO de arriba.
        config.setTipoUmbral(TipoUmbral.SEMANAL);
        List<Schedule> schedules = List.of(
                shift(2026, 8, 3, 9, 0, 17, 0),  // lunes
                shift(2026, 8, 4, 9, 0, 17, 0),  // martes
                shift(2026, 8, 5, 9, 0, 17, 0),  // miércoles
                shift(2026, 8, 6, 9, 0, 17, 0),  // jueves
                shift(2026, 8, 7, 9, 0, 17, 0)   // viernes — misma semana ISO
        );
        PayslipCalculation result = builder().withSchedules(schedules).build();

        // 40h totales, umbral=8 → 8 normales + 32 extra
        assertEquals(bd("8.00"), result.horasNormales());
        assertEquals(bd("32.00"), result.horasExtra());
    }

    @Test
    void umbralSemanal_turnosEnSemanasDistintasNoSeAcumulanEntreSi() {
        config.setTipoUmbral(TipoUmbral.SEMANAL);
        List<Schedule> schedules = List.of(
                shift(2026, 8, 7, 9, 0, 17, 0),   // viernes, semana 1
                shift(2026, 8, 10, 9, 0, 17, 0)   // lunes siguiente, semana 2
        );
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("16.00"), result.horasNormales());
        assertEquals(bd("0.00"), result.horasExtra());
    }

    @Test
    void turnoNoCumplidoNoContabiliza() {
        Schedule planificado = shift(2026, 8, 4, 9, 0, 17, 0);
        planificado.setEstado(EstadoTurno.PLANIFICADO);
        PayslipCalculation result = builder().withSchedules(List.of(planificado)).build();

        assertEquals(bd("0.00"), result.brutoCalculado());
    }

    // ── Paso 2: licencias ─────────────────────────────────────────────────────

    @Test
    void licenciaPaga_sumaAlBruto() {
        // Vacaciones pagas: 5 días en el período → +5 × 8h × 1000 = +40000
        LeaveRequest lr = leaveRequest(
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 8), true);
        PayslipCalculation result = builder().withLeaveRequests(List.of(lr)).build();

        // 5 días × 8h × 1000/h = 40000
        assertEquals(bd("40000.00"), result.ajusteLicencias());
        assertEquals(bd("40000.00"), result.brutoCalculado());
    }

    @Test
    void licenciaNoPaga_restaDelBruto() {
        // Ausencia no justificada: 2 días → -2 × 8 × 1000 = -16000
        // Pero hay una jornada de 8h normal (8000) → bruto = 8000 - 16000 = 0 (max con 0)
        List<Schedule> schedules = List.of(shift(2026, 8, 4, 9, 0, 17, 0)); // 8h
        LeaveRequest lr = leaveRequest(
                LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 6), false); // 2 días
        PayslipCalculation result = builder()
                .withSchedules(schedules)
                .withLeaveRequests(List.of(lr))
                .build();

        assertEquals(bd("-16000.00"), result.ajusteLicencias());
        // brutoHoras=8000, ajuste=-16000 → max con 0
        assertEquals(bd("0.00"), result.brutoCalculado());
    }

    @Test
    void licenciaParcialmenteEnPeriodo_soloCuentaParteSolapada() {
        // Licencia: 28/7 al 3/8 → en el período (1/8–31/8) solo solapan 3/8 al 3/8 = 3 días
        LeaveRequest lr = leaveRequest(
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 3), true);
        PayslipCalculation result = builder().withLeaveRequests(List.of(lr)).build();

        // 3 días × 8h × 1000 = 24000
        assertEquals(bd("24000.00"), result.ajusteLicencias());
    }

    // ── Paso 3: descuentos por conceptos ─────────────────────────────────────

    @Test
    void descuentosPorcentaje_aplicadosSobreBruto() {
        List<Schedule> schedules = List.of(shift(2026, 8, 4, 9, 0, 17, 0)); // 8h → bruto 8000
        config.setConceptosDescuento(List.of(
                concepto("Obra Social", TipoConcepto.PORCENTAJE, bd("10"))
        ));
        PayslipCalculation result = builder().withSchedules(schedules).build();

        // deducción = 10% de 8000 = 800
        assertEquals(1, result.deducciones().size());
        assertEquals(bd("800.00"), result.deducciones().get(0).monto());
        assertEquals(bd("800.00"), result.totalDeducciones());
        assertEquals(bd("7200.00"), result.netoFinal());
    }

    @Test
    void descuentoMontoFijo() {
        List<Schedule> schedules = List.of(shift(2026, 8, 4, 9, 0, 17, 0)); // bruto 8000
        config.setConceptosDescuento(List.of(
                concepto("Cuota sindicato", TipoConcepto.MONTO_FIJO, bd("500"))
        ));
        PayslipCalculation result = builder().withSchedules(schedules).build();

        assertEquals(bd("500.00"), result.totalDeducciones());
        assertEquals(bd("7500.00"), result.netoFinal());
    }

    // ── Paso 4: adelantos ─────────────────────────────────────────────────────

    @Test
    void conAdelanto_descontadoDelNeto() {
        List<Schedule> schedules = List.of(shift(2026, 8, 4, 9, 0, 17, 0)); // bruto 8000
        Advance advance = advance(bd("2000.00"));
        PayslipCalculation result = builder()
                .withSchedules(schedules)
                .withAdvances(List.of(advance))
                .build();

        assertEquals(bd("2000.00"), result.totalAdelantos());
        assertEquals(1, result.adelantosAplicados().size());
        assertEquals(bd("6000.00"), result.netoFinal());
    }

    @Test
    void flujoCompleto() {
        // 8h normales + feriado + licencia paga 1 día + descuento 10% + adelanto
        LocalDate feriado = LocalDate.of(2026, 8, 11);
        List<Schedule> schedules = List.of(
                shift(2026, 8, 4,  9, 0, 17, 0),  // 8h normal → 8000
                shift(2026, 8, 11, 9, 0, 17, 0)   // 8h feriado → 16000
        );
        LeaveRequest lr = leaveRequest(
                LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 18), true); // 1 día pago → +8000
        config.setConceptosDescuento(List.of(
                concepto("Obra Social", TipoConcepto.PORCENTAJE, bd("10"))
        ));
        Advance advance = advance(bd("1000.00"));

        PayslipCalculation result = builder()
                .withSchedules(schedules)
                .withHolidays(List.of(feriado))
                .withLeaveRequests(List.of(lr))
                .withAdvances(List.of(advance))
                .build();

        // brutoHoras = 8000 + 16000 = 24000
        assertEquals(bd("24000.00"), result.brutoHoras());
        // ajuste licencias = +8000
        assertEquals(bd("8000.00"),  result.ajusteLicencias());
        // brutoCalculado = 32000
        assertEquals(bd("32000.00"), result.brutoCalculado());
        // deducción 10% de 32000 = 3200
        assertEquals(bd("3200.00"), result.totalDeducciones());
        // tras descuentos = 28800
        // adelanto = 1000
        assertEquals(bd("27800.00"), result.netoFinal());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PayslipBuilder builder() {
        return new PayslipBuilder()
                .withEmployee(employee)
                .withPeriod(period)
                .withConfig(config);
    }

    private Schedule shift(int year, int month, int day,
                           int startH, int startM, int endH, int endM) {
        Schedule s = new Schedule();
        s.setCompanyId(UUID.randomUUID());
        s.setEmployee(employee);
        s.setBranch(branch);
        s.setFechaHoraInicio(LocalDateTime.of(year, month, day, startH, startM));
        s.setFechaHoraFin(LocalDateTime.of(year, month, day, endH, endM));
        s.setTipoTurno(TipoTurno.FIJO);
        s.setEstado(EstadoTurno.CUMPLIDO);
        return s;
    }

    private LeaveRequest leaveRequest(LocalDate ini, LocalDate fin, boolean paga) {
        LeaveType lt = new LeaveType();
        lt.setNombre(paga ? "Vacaciones" : "Ausencia");
        lt.setEsPaga(paga);

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(employee);
        lr.setLeaveType(lt);
        lr.setFechaInicio(ini);
        lr.setFechaFin(fin);
        lr.setEstado(EstadoLicencia.APROBADA);
        return lr;
    }

    private Advance advance(BigDecimal monto) {
        Advance a = new Advance();
        a.setEmployee(employee);
        a.setMonto(monto);
        a.setEstado(EstadoAdelanto.PENDIENTE);
        a.setFecha(PERIOD_START);
        return a;
    }

    private static ConceptoDescuento concepto(String nombre, TipoConcepto tipo, BigDecimal valor) {
        ConceptoDescuento c = new ConceptoDescuento();
        c.setNombre(nombre);
        c.setTipo(tipo);
        c.setValor(valor);
        return c;
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
