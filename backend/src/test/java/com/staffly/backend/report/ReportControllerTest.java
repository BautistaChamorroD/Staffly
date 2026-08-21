package com.staffly.backend.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.advance.Advance;
import com.staffly.backend.advance.EstadoAdelanto;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
import com.staffly.backend.holiday.Holiday;
import com.staffly.backend.payroll.EstadoPeriodo;
import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payroll.Periodicidad;
import com.staffly.backend.payroll.TipoUmbral;
import com.staffly.backend.payslip.EstadoRecibo;
import com.staffly.backend.payslip.Payslip;
import com.staffly.backend.payslip.TipoRecibo;
import com.staffly.backend.schedule.EstadoTurno;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.TipoTurno;
import com.staffly.backend.user.EstadoUsuario;
import com.staffly.backend.user.RolUsuario;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/reports";

    @Autowired private MockMvc         mockMvc;
    @Autowired private ObjectMapper    objectMapper;
    @Autowired private EntityManager   entityManager;
    @Autowired private UserRepository  userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID     companyAId;
    private Branch   branch1;
    private Branch   branch2;
    private Employee emp1;
    private String   adminAToken;
    private String   rrhhAToken;
    private String   supervisorAToken;
    private String   empToken1;

    @BeforeEach
    void seed() throws Exception {
        companyAId = createCompany("Empresa A");
        branch1 = createBranch(companyAId, "Sucursal 1");
        branch2 = createBranch(companyAId, "Sucursal 2");
        emp1 = createEmployee(companyAId, branch1, "Juan", "Pérez");
        createPayrollConfig(companyAId, TipoUmbral.DIARIO);
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN, null, null);
        rrhhAToken = createUserAndLogin(companyAId, "rrhh@a.com", RolUsuario.RRHH, null, null);
        supervisorAToken = createUserAndLogin(companyAId, "sup@a.com", RolUsuario.SUPERVISOR, null, branch1);
        empToken1 = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, emp1, null);
    }

    // ── RBAC ──────────────────────────────────────────────────────────────────

    @Test
    void adminPuedeConsultar() throws Exception {
        mockMvc.perform(get(BASE_URL + "/hours-worked").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());
    }

    @Test
    void rrhhPuedeConsultar() throws Exception {
        mockMvc.perform(get(BASE_URL + "/hours-worked").header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isOk());
    }

    @Test
    void supervisorNoPuedeConsultar() throws Exception {
        mockMvc.perform(get(BASE_URL + "/hours-worked").header("Authorization", "Bearer " + supervisorAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeNoPuedeConsultar() throws Exception {
        mockMvc.perform(get(BASE_URL + "/hours-worked").header("Authorization", "Bearer " + empToken1))
                .andExpect(status().isForbidden());
    }

    // ── cálculo ───────────────────────────────────────────────────────────────

    @Test
    void turnoCumplidoDentroDelUmbral_todoNormal() throws Exception {
        createSchedule(companyAId, emp1, branch1, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 9, 0), LocalDateTime.of(2026, 7, 6, 17, 0)); // 8h

        mockMvc.perform(get(BASE_URL + "/hours-worked?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(emp1.getId().toString()))
                .andExpect(jsonPath("$[0].branchId").value(branch1.getId().toString()))
                .andExpect(jsonPath("$[0].horasNormales").value(8.0))
                .andExpect(jsonPath("$[0].horasExtra").value(0.0))
                .andExpect(jsonPath("$[0].horasFeriado").value(0.0))
                .andExpect(jsonPath("$[0].totalHoras").value(8.0));
    }

    @Test
    void turnoSuperaUmbralDiario_calculaHoraExtra() throws Exception {
        createSchedule(companyAId, emp1, branch1, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 8, 0), LocalDateTime.of(2026, 7, 6, 18, 0)); // 10h

        mockMvc.perform(get(BASE_URL + "/hours-worked?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].horasNormales").value(8.0))
                .andExpect(jsonPath("$[0].horasExtra").value(2.0))
                .andExpect(jsonPath("$[0].totalHoras").value(10.0));
    }

    @Test
    void turnoEnFeriado_cuentaComoHorasFeriado() throws Exception {
        Holiday h = new Holiday();
        h.setCompanyId(companyAId);
        h.setFecha(LocalDate.of(2026, 7, 9));
        h.setNombre("Feriado test");
        h.setRecurrente(false);
        entityManager.persist(h);

        createSchedule(companyAId, emp1, branch1, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 9, 9, 0), LocalDateTime.of(2026, 7, 9, 17, 0)); // 8h feriado

        mockMvc.perform(get(BASE_URL + "/hours-worked?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].horasNormales").value(0.0))
                .andExpect(jsonPath("$[0].horasFeriado").value(8.0))
                .andExpect(jsonPath("$[0].totalHoras").value(8.0));
    }

    @Test
    void turnoNoCumplidoNoContabiliza() throws Exception {
        createSchedule(companyAId, emp1, branch1, EstadoTurno.PLANIFICADO,
                LocalDateTime.of(2026, 7, 6, 9, 0), LocalDateTime.of(2026, 7, 6, 17, 0));

        mockMvc.perform(get(BASE_URL + "/hours-worked?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void turnoFueraDelRangoNoContabiliza() throws Exception {
        createSchedule(companyAId, emp1, branch1, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 6, 6, 9, 0), LocalDateTime.of(2026, 6, 6, 17, 0));

        mockMvc.perform(get(BASE_URL + "/hours-worked?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void filtroBranchIdExcluyeOtrasSucursales() throws Exception {
        Employee emp2 = createEmployee(companyAId, branch2, "Ana", "García");
        createSchedule(companyAId, emp1, branch1, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 9, 0), LocalDateTime.of(2026, 7, 6, 17, 0));
        createSchedule(companyAId, emp2, branch2, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 9, 0), LocalDateTime.of(2026, 7, 6, 17, 0));

        mockMvc.perform(get(BASE_URL + "/hours-worked?branchId=" + branch1.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchId").value(branch1.getId().toString()));
    }

    @Test
    void empleadoMultiSucursal_calculaHorasExtraANivelEmpleado() throws Exception {
        emp1.getBranches().add(branch2);
        entityManager.merge(emp1);
        entityManager.flush();

        createSchedule(companyAId, emp1, branch1, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 8, 0), LocalDateTime.of(2026, 7, 6, 14, 0)); // 6h
        createSchedule(companyAId, emp1, branch2, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 14, 0), LocalDateTime.of(2026, 7, 6, 20, 0)); // +6h = 12h

        mockMvc.perform(get(BASE_URL + "/hours-worked?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].branchId").value(branch1.getId().toString()))
                .andExpect(jsonPath("$[0].horasNormales").value(6.0))
                .andExpect(jsonPath("$[0].horasExtra").value(0.0))
                .andExpect(jsonPath("$[1].branchId").value(branch2.getId().toString()))
                .andExpect(jsonPath("$[1].horasNormales").value(2.0))
                .andExpect(jsonPath("$[1].horasExtra").value(4.0));
    }

    @Test
    void filtroBranchIdMantieneHorasExtraCalculadasANivelEmpleado() throws Exception {
        emp1.getBranches().add(branch2);
        entityManager.merge(emp1);
        entityManager.flush();

        createSchedule(companyAId, emp1, branch1, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 8, 0), LocalDateTime.of(2026, 7, 6, 14, 0)); // 6h
        createSchedule(companyAId, emp1, branch2, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 14, 0), LocalDateTime.of(2026, 7, 6, 20, 0)); // +6h = 12h

        mockMvc.perform(get(BASE_URL + "/hours-worked?branchId=" + branch2.getId()
                        + "&desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchId").value(branch2.getId().toString()))
                .andExpect(jsonPath("$[0].horasNormales").value(2.0))
                .andExpect(jsonPath("$[0].horasExtra").value(4.0))
                .andExpect(jsonPath("$[0].totalHoras").value(6.0));
    }

    @Test
    void sinConfigDeNominaRetorna400() throws Exception {
        UUID companyBId = createCompany("Empresa Sin Config");
        String adminBToken = createUserAndLogin(companyBId, "admin@b.com", RolUsuario.ADMIN, null, null);

        mockMvc.perform(get(BASE_URL + "/hours-worked").header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantIsolation() throws Exception {
        UUID companyBId = createCompany("Empresa B");
        Branch branchB = createBranch(companyBId, "Sucursal B");
        Employee empB = createEmployee(companyBId, branchB, "Otro", "Empleado");
        createPayrollConfig(companyBId, TipoUmbral.DIARIO);
        createSchedule(companyBId, empB, branchB, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 9, 0), LocalDateTime.of(2026, 7, 6, 17, 0));

        mockMvc.perform(get(BASE_URL + "/hours-worked?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── RBAC payroll-cost ────────────────────────────────────────────────────

    @Test
    void adminPuedeConsultarPayrollCost() throws Exception {
        mockMvc.perform(get(BASE_URL + "/payroll-cost").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());
    }

    @Test
    void rrhhPuedeConsultarPayrollCost() throws Exception {
        mockMvc.perform(get(BASE_URL + "/payroll-cost").header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isOk());
    }

    @Test
    void supervisorNoPuedeConsultarPayrollCost() throws Exception {
        mockMvc.perform(get(BASE_URL + "/payroll-cost").header("Authorization", "Bearer " + supervisorAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeNoPuedeConsultarPayrollCost() throws Exception {
        mockMvc.perform(get(BASE_URL + "/payroll-cost").header("Authorization", "Bearer " + empToken1))
                .andExpect(status().isForbidden());
    }

    // ── cálculo payroll-cost ─────────────────────────────────────────────────

    @Test
    void periodoCerrado_agrupaPorSucursal() throws Exception {
        PayrollPeriod period = createPayrollPeriod(companyAId, EstadoPeriodo.CERRADO,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        createPayslip(companyAId, emp1, period, EstadoRecibo.PAGADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(200_000), BigDecimal.valueOf(20_000), BigDecimal.valueOf(180_000));

        mockMvc.perform(get(BASE_URL + "/payroll-cost?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchId").value(branch1.getId().toString()))
                .andExpect(jsonPath("$[0].cantidadEmpleados").value(1))
                .andExpect(jsonPath("$[0].totalBruto").value(200000.0))
                .andExpect(jsonPath("$[0].totalDeducciones").value(20000.0))
                .andExpect(jsonPath("$[0].totalNeto").value(180000.0));
    }

    @Test
    void periodoAbiertoNoContabiliza() throws Exception {
        PayrollPeriod period = createPayrollPeriod(companyAId, EstadoPeriodo.ABIERTO,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        createPayslip(companyAId, emp1, period, EstadoRecibo.GENERADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(200_000), BigDecimal.valueOf(20_000), BigDecimal.valueOf(180_000));

        mockMvc.perform(get(BASE_URL + "/payroll-cost?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void payslipAnuladoNoContabiliza() throws Exception {
        PayrollPeriod period = createPayrollPeriod(companyAId, EstadoPeriodo.CERRADO,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        createPayslip(companyAId, emp1, period, EstadoRecibo.ANULADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(200_000), BigDecimal.valueOf(20_000), BigDecimal.valueOf(180_000));

        mockMvc.perform(get(BASE_URL + "/payroll-cost?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void filtroBranchIdPayrollCostExcluyeOtrasSucursales() throws Exception {
        Employee emp2 = createEmployee(companyAId, branch2, "Ana", "García");
        PayrollPeriod period = createPayrollPeriod(companyAId, EstadoPeriodo.CERRADO,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        createPayslip(companyAId, emp1, period, EstadoRecibo.PAGADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(200_000), BigDecimal.valueOf(20_000), BigDecimal.valueOf(180_000));
        createPayslip(companyAId, emp2, period, EstadoRecibo.PAGADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(300_000), BigDecimal.valueOf(30_000), BigDecimal.valueOf(270_000));

        mockMvc.perform(get(BASE_URL + "/payroll-cost?branchId=" + branch1.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchId").value(branch1.getId().toString()))
                .andExpect(jsonPath("$[0].totalBruto").value(200000.0));
    }

    @Test
    void periodoFueraDelRangoNoContabiliza() throws Exception {
        PayrollPeriod period = createPayrollPeriod(companyAId, EstadoPeriodo.CERRADO,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        createPayslip(companyAId, emp1, period, EstadoRecibo.PAGADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(200_000), BigDecimal.valueOf(20_000), BigDecimal.valueOf(180_000));

        mockMvc.perform(get(BASE_URL + "/payroll-cost?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void empleadoConDosSucursales_vaAGrupoSinSucursalUnica() throws Exception {
        emp1.getBranches().add(branch2);
        entityManager.merge(emp1);
        entityManager.flush();

        PayrollPeriod period = createPayrollPeriod(companyAId, EstadoPeriodo.CERRADO,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        createPayslip(companyAId, emp1, period, EstadoRecibo.PAGADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(200_000), BigDecimal.valueOf(20_000), BigDecimal.valueOf(180_000));

        mockMvc.perform(get(BASE_URL + "/payroll-cost?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchId").doesNotExist());
    }

    @Test
    void tenantIsolationPayrollCost() throws Exception {
        UUID companyBId = createCompany("Empresa B Costo");
        Branch branchB = createBranch(companyBId, "Sucursal B");
        Employee empB = createEmployee(companyBId, branchB, "Otro", "Empleado");
        PayrollPeriod periodB = createPayrollPeriod(companyBId, EstadoPeriodo.CERRADO,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        createPayslip(companyBId, empB, periodB, EstadoRecibo.PAGADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(100_000), BigDecimal.valueOf(10_000), BigDecimal.valueOf(90_000));

        mockMvc.perform(get(BASE_URL + "/payroll-cost?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── RBAC pending-advances ────────────────────────────────────────────────

    @Test
    void adminPuedeConsultarPendingAdvances() throws Exception {
        mockMvc.perform(get(BASE_URL + "/pending-advances").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());
    }

    @Test
    void rrhhPuedeConsultarPendingAdvances() throws Exception {
        mockMvc.perform(get(BASE_URL + "/pending-advances").header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isOk());
    }

    @Test
    void supervisorNoPuedeConsultarPendingAdvances() throws Exception {
        mockMvc.perform(get(BASE_URL + "/pending-advances").header("Authorization", "Bearer " + supervisorAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeNoPuedeConsultarPendingAdvances() throws Exception {
        mockMvc.perform(get(BASE_URL + "/pending-advances").header("Authorization", "Bearer " + empToken1))
                .andExpect(status().isForbidden());
    }

    // ── cálculo pending-advances ─────────────────────────────────────────────

    @Test
    void adelantoPendienteApareceEnElReporte() throws Exception {
        Advance adv = createAdvance(companyAId, emp1, EstadoAdelanto.PENDIENTE,
                LocalDate.of(2026, 7, 15), BigDecimal.valueOf(50_000), "Adelanto de sueldo");

        mockMvc.perform(get(BASE_URL + "/pending-advances").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(adv.getId().toString()))
                .andExpect(jsonPath("$[0].employeeId").value(emp1.getId().toString()))
                .andExpect(jsonPath("$[0].employeeNombre").value("Juan"))
                .andExpect(jsonPath("$[0].employeeApellido").value("Pérez"))
                .andExpect(jsonPath("$[0].monto").value(50000.0))
                .andExpect(jsonPath("$[0].fecha").value("2026-07-15"))
                .andExpect(jsonPath("$[0].motivo").value("Adelanto de sueldo"));
    }

    @Test
    void adelantoDescontadoNoContabiliza() throws Exception {
        createAdvance(companyAId, emp1, EstadoAdelanto.DESCONTADO,
                LocalDate.of(2026, 7, 15), BigDecimal.valueOf(50_000), "Ya descontado");

        mockMvc.perform(get(BASE_URL + "/pending-advances").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adelantoCanceladoNoContabiliza() throws Exception {
        createAdvance(companyAId, emp1, EstadoAdelanto.CANCELADO,
                LocalDate.of(2026, 7, 15), BigDecimal.valueOf(50_000), "Cancelado");

        mockMvc.perform(get(BASE_URL + "/pending-advances").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void tenantIsolationPendingAdvances() throws Exception {
        UUID companyBId = createCompany("Empresa B Adelantos");
        Branch branchB = createBranch(companyBId, "Sucursal B");
        Employee empB = createEmployee(companyBId, branchB, "Otro", "Empleado");
        createAdvance(companyBId, empB, EstadoAdelanto.PENDIENTE,
                LocalDate.of(2026, 7, 15), BigDecimal.valueOf(10_000), "Otro adelanto");

        mockMvc.perform(get(BASE_URL + "/pending-advances").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── export ────────────────────────────────────────────────────────────────

    @Test
    void adminPuedeExportarHoursWorkedCsv() throws Exception {
        createSchedule(companyAId, emp1, branch1, EstadoTurno.CUMPLIDO,
                LocalDateTime.of(2026, 7, 6, 9, 0), LocalDateTime.of(2026, 7, 6, 17, 0));

        String csv = mockMvc.perform(get(BASE_URL + "/hours-worked/export?format=csv&desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=hours-worked.csv"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(csv).contains("employeeNombre");
        org.assertj.core.api.Assertions.assertThat(csv).contains("Juan");
    }

    @Test
    void adminPuedeExportarPayrollCostPdf() throws Exception {
        PayrollPeriod period = createPayrollPeriod(companyAId, EstadoPeriodo.CERRADO,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        createPayslip(companyAId, emp1, period, EstadoRecibo.PAGADO, TipoRecibo.NORMAL,
                BigDecimal.valueOf(200_000), BigDecimal.valueOf(20_000), BigDecimal.valueOf(180_000));

        byte[] pdf = mockMvc.perform(get(BASE_URL + "/payroll-cost/export?format=pdf&desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=payroll-cost.pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void rrhhPuedeExportarPendingAdvancesCsv() throws Exception {
        createAdvance(companyAId, emp1, EstadoAdelanto.PENDIENTE,
                LocalDate.of(2026, 7, 15), BigDecimal.valueOf(50_000), "Adelanto de sueldo");

        String csv = mockMvc.perform(get(BASE_URL + "/pending-advances/export?format=csv")
                        .header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(csv).contains("Adelanto de sueldo");
    }

    @Test
    void supervisorNoPuedeExportar() throws Exception {
        mockMvc.perform(get(BASE_URL + "/hours-worked/export?format=csv")
                        .header("Authorization", "Bearer " + supervisorAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeNoPuedeExportar() throws Exception {
        mockMvc.perform(get(BASE_URL + "/hours-worked/export?format=csv")
                        .header("Authorization", "Bearer " + empToken1))
                .andExpect(status().isForbidden());
    }

    @Test
    void formatoInvalidoRetorna400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/hours-worked/export?format=xml")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reporteDesconocidoRetorna400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/no-existe/export?format=csv")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID createCompany(String nombre) {
        Company c = new Company();
        c.setNombre(nombre);
        c.setRazonSocial(nombre + " SRL");
        c.setPais("AR");
        c.setMoneda("ARS");
        c.setZonaHoraria("America/Argentina/Buenos_Aires");
        c.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(c);
        entityManager.flush();
        return c.getId();
    }

    private Branch createBranch(UUID companyId, String nombre) {
        Branch b = new Branch();
        b.setCompanyId(companyId);
        b.setNombre(nombre);
        b.setDireccion("Dirección");
        b.setZonaHoraria("America/Argentina/Buenos_Aires");
        b.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(b);
        entityManager.flush();
        return b;
    }

    private Employee createEmployee(UUID companyId, Branch branch, String nombre, String apellido) {
        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setNombre(nombre);
        e.setApellido(apellido);
        e.setDocumento(UUID.randomUUID().toString().substring(0, 8));
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setSueldoBase(BigDecimal.valueOf(200_000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        e.getBranches().add(branch);
        entityManager.persist(e);
        entityManager.flush();
        return e;
    }

    private void createPayrollConfig(UUID companyId, TipoUmbral tipoUmbral) {
        PayrollConfig cfg = new PayrollConfig();
        cfg.setCompanyId(companyId);
        cfg.setUmbralHorasExtra(BigDecimal.valueOf(8));
        cfg.setTipoUmbral(tipoUmbral);
        cfg.setMultiplicadorHoraExtra(BigDecimal.valueOf(1.5));
        cfg.setMultiplicadorFeriado(BigDecimal.valueOf(2.0));
        cfg.setPeriodicidad(Periodicidad.MENSUAL);
        entityManager.persist(cfg);
        entityManager.flush();
    }

    private Schedule createSchedule(UUID companyId, Employee emp, Branch branch, EstadoTurno estado,
                                    LocalDateTime inicio, LocalDateTime fin) {
        Schedule s = new Schedule();
        s.setCompanyId(companyId);
        s.setEmployee(emp);
        s.setBranch(branch);
        s.setFechaHoraInicio(inicio);
        s.setFechaHoraFin(fin);
        s.setTipoTurno(TipoTurno.FIJO);
        s.setEstado(estado);
        entityManager.persist(s);
        entityManager.flush();
        return s;
    }

    private PayrollPeriod createPayrollPeriod(UUID companyId, EstadoPeriodo estado,
                                              LocalDate fechaInicio, LocalDate fechaFin) {
        PayrollPeriod p = new PayrollPeriod();
        p.setCompanyId(companyId);
        p.setFechaInicio(fechaInicio);
        p.setFechaFin(fechaFin);
        p.setEstado(estado);
        entityManager.persist(p);
        entityManager.flush();
        return p;
    }

    private Payslip createPayslip(UUID companyId, Employee emp, PayrollPeriod period, EstadoRecibo estado,
                                  TipoRecibo tipo, BigDecimal bruto, BigDecimal deducciones, BigDecimal neto) {
        Payslip p = new Payslip();
        p.setCompanyId(companyId);
        p.setEmployee(emp);
        p.setPayrollPeriod(period);
        p.setEstado(estado);
        p.setTipo(tipo);
        p.setSueldoBase(emp.getSueldoBase());
        p.setHorasNormales(BigDecimal.ZERO);
        p.setHorasExtra(BigDecimal.ZERO);
        p.setHorasFeriado(BigDecimal.ZERO);
        p.setMontoHorasNormales(BigDecimal.ZERO);
        p.setMontoHorasExtra(BigDecimal.ZERO);
        p.setMontoHorasFeriado(BigDecimal.ZERO);
        p.setBrutoCalculado(bruto);
        p.setTotalDeducciones(deducciones);
        p.setTotalAdelantos(BigDecimal.ZERO);
        p.setNetoFinal(neto);
        p.setDetalleDescuentos(List.of());
        p.setAdelantosAplicados(List.of());
        entityManager.persist(p);
        entityManager.flush();
        return p;
    }

    private Advance createAdvance(UUID companyId, Employee emp, EstadoAdelanto estado,
                                  LocalDate fecha, BigDecimal monto, String motivo) {
        Advance a = new Advance();
        a.setCompanyId(companyId);
        a.setEmployee(emp);
        a.setFecha(fecha);
        a.setMonto(monto);
        a.setMotivo(motivo);
        a.setEstado(estado);
        entityManager.persist(a);
        entityManager.flush();
        return a;
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol,
                                      Employee employee, Branch assignedBranch) throws Exception {
        User u = new User();
        u.setCompanyId(companyId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRol(rol);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setDebeCambiarPassword(false);
        if (employee != null) u.setEmployee(employee);
        if (assignedBranch != null) u.getBranches().add(assignedBranch);
        userRepository.save(u);
        entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResp).get("accessToken").asText();
    }
}
