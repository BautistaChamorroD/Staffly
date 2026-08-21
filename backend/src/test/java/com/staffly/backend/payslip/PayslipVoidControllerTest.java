package com.staffly.backend.payslip;

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
import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.PayrollConfigRepository;
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payroll.EstadoPeriodo;
import com.staffly.backend.payroll.Periodicidad;
import com.staffly.backend.payroll.TipoUmbral;
import com.staffly.backend.payslip.builder.PayslipCalculation;
import com.staffly.backend.user.EstadoUsuario;
import com.staffly.backend.user.RolUsuario;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayslipVoidControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL  = "/api/v1/payslips";

    @Autowired private MockMvc              mockMvc;
    @Autowired private ObjectMapper         objectMapper;
    @Autowired private EntityManager        entityManager;
    @Autowired private UserRepository       userRepository;
    @Autowired private PasswordEncoder      passwordEncoder;
    @Autowired private PayrollConfigRepository configRepository;

    private UUID          companyAId;
    private Branch        branchA;
    private Employee      empA1;
    private PayrollPeriod periodA;
    private String        adminAToken;
    private String        rrhhAToken;

    @BeforeEach
    void seed() throws Exception {
        companyAId  = createCompany("Empresa A");
        branchA     = createBranch(companyAId, "Sucursal A");
        empA1       = createEmployee(companyAId, branchA, "Juan", "Pérez");
        periodA     = createPeriod(companyAId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        createPayrollConfig(companyAId);
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN, null);
        rrhhAToken  = createUserAndLogin(companyAId, "rrhh@a.com",  RolUsuario.RRHH,  null);
    }

    @Test
    void voidPagadoCreatesAjusteAndAnulaOriginal() throws Exception {
        Payslip original = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        entityManager.flush();

        String resp = mockMvc.perform(post(BASE_URL + "/" + original.getId() + "/void")
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("motivoAnulacion", "Error en horas"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.estado").value("GENERADO"))
                .andExpect(jsonPath("$.payslipOriginalId").value(original.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        entityManager.flush();
        entityManager.clear();

        Payslip refreshed = entityManager.find(Payslip.class, original.getId());
        assertThat(refreshed.getEstado()).isEqualTo(EstadoRecibo.ANULADO);
        assertThat(refreshed.getMotivoAnulacion()).isEqualTo("Error en horas");

        mockMvc.perform(get("/api/v1/audit-log?entidad=PAYSLIP&entidadId=" + original.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].campo").value("estado"))
                .andExpect(jsonPath("$[0].valorAnterior").value("PAGADO"))
                .andExpect(jsonPath("$[0].valorNuevo").value("ANULADO"));
    }

    @Test
    void voidPreservesOriginalAppliedAdvancesInAdjustment() throws Exception {
        Advance originalAdvance = createAdvance(empA1, LocalDate.of(2026, 6, 10),
                BigDecimal.valueOf(5000), EstadoAdelanto.DESCONTADO);
        originalAdvance.setPayrollPeriod(periodA);

        Advance pendingAdvance = createAdvance(empA1, LocalDate.of(2026, 6, 20),
                BigDecimal.valueOf(3000), EstadoAdelanto.PENDIENTE);

        Payslip original = createPayslipWithAdvance(companyAId, empA1, periodA, EstadoRecibo.PAGADO,
                originalAdvance.getId(), originalAdvance.getMonto());
        entityManager.flush();

        String resp = mockMvc.perform(post(BASE_URL + "/" + original.getId() + "/void")
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("motivoAnulacion", "Ajuste con adelanto"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.estado").value("GENERADO"))
                .andExpect(jsonPath("$.totalAdelantos").value(5000.00))
                .andReturn().getResponse().getContentAsString();

        UUID adjustmentId = UUID.fromString(objectMapper.readTree(resp).get("id").asText());

        entityManager.flush();
        entityManager.clear();

        Payslip adjustment = entityManager.find(Payslip.class, adjustmentId);
        assertThat(adjustment.getAdelantosAplicados()).containsExactly(originalAdvance.getId());

        Advance refreshedOriginalAdvance = entityManager.find(Advance.class, originalAdvance.getId());
        Advance refreshedPendingAdvance = entityManager.find(Advance.class, pendingAdvance.getId());
        assertThat(refreshedOriginalAdvance.getEstado()).isEqualTo(EstadoAdelanto.DESCONTADO);
        assertThat(refreshedPendingAdvance.getEstado()).isEqualTo(EstadoAdelanto.PENDIENTE);
    }

    @Test
    void voidGeneradoReturns422() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + p.getId() + "/void")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void voidAnuladoReturns422() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.ANULADO);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + p.getId() + "/void")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rrhhCannotVoid() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + p.getId() + "/void")
                        .header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void voidWithoutBodyStillWorks() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + p.getId() + "/void")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.estado").value("GENERADO"));
    }

    @Test
    void tenantIsolation() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        entityManager.flush();

        UUID companyBId    = createCompany("Empresa B");
        String adminBToken = createUserAndLogin(companyBId, "admin@b.com", RolUsuario.ADMIN, null);

        mockMvc.perform(post(BASE_URL + "/" + p.getId() + "/void")
                        .header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID createCompany(String nombre) {
        Company c = new Company();
        c.setNombre(nombre); c.setRazonSocial(nombre + " SRL");
        c.setPais("AR"); c.setMoneda("ARS");
        c.setZonaHoraria("America/Argentina/Buenos_Aires");
        c.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(c); entityManager.flush();
        return c.getId();
    }

    private Branch createBranch(UUID companyId, String nombre) {
        Branch b = new Branch();
        b.setCompanyId(companyId); b.setNombre(nombre);
        b.setDireccion("Dir"); b.setZonaHoraria("America/Argentina/Buenos_Aires");
        b.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(b); entityManager.flush();
        return b;
    }

    private Employee createEmployee(UUID companyId, Branch branch, String nombre, String apellido) {
        Employee e = new Employee();
        e.setCompanyId(companyId); e.setNombre(nombre); e.setApellido(apellido);
        e.setDocumento(UUID.randomUUID().toString().substring(0, 8));
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setSueldoBase(BigDecimal.valueOf(100_000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        e.getBranches().add(branch);
        entityManager.persist(e); entityManager.flush();
        return e;
    }

    private PayrollPeriod createPeriod(UUID companyId, LocalDate ini, LocalDate fin) {
        PayrollPeriod pp = new PayrollPeriod();
        pp.setCompanyId(companyId);
        pp.setFechaInicio(ini); pp.setFechaFin(fin);
        pp.setEstado(EstadoPeriodo.REABIERTO);
        entityManager.persist(pp); entityManager.flush();
        return pp;
    }

    private void createPayrollConfig(UUID companyId) {
        PayrollConfig cfg = new PayrollConfig();
        cfg.setCompanyId(companyId);
        cfg.setUmbralHorasExtra(BigDecimal.valueOf(160));
        cfg.setTipoUmbral(TipoUmbral.DIARIO);
        cfg.setMultiplicadorHoraExtra(BigDecimal.valueOf(1.5));
        cfg.setMultiplicadorFeriado(BigDecimal.valueOf(2.0));
        cfg.setPeriodicidad(Periodicidad.MENSUAL);
        entityManager.persist(cfg); entityManager.flush();
    }

    private Payslip createPayslip(UUID companyId, Employee emp, PayrollPeriod period,
                                   EstadoRecibo estado) {
        PayslipCalculation calc = new PayslipCalculation(
                emp.getId(), period.getId(),
                emp.getSueldoBase(), BigDecimal.valueOf(500),
                BigDecimal.valueOf(160), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(80_000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(80_000), BigDecimal.ZERO, BigDecimal.valueOf(80_000),
                List.of(), BigDecimal.ZERO, List.of(), BigDecimal.ZERO,
                BigDecimal.valueOf(80_000)
        );
        Payslip p = PayslipFactory.normal(companyId, emp, period, calc);
        p.setEstado(estado);
        if (estado == EstadoRecibo.PAGADO) p.setFechaPago(LocalDate.of(2026, 6, 30));
        entityManager.persist(p); entityManager.flush();
        return p;
    }

    private Payslip createPayslipWithAdvance(UUID companyId, Employee emp, PayrollPeriod period,
                                             EstadoRecibo estado, UUID advanceId, BigDecimal advanceAmount) {
        PayslipCalculation calc = new PayslipCalculation(
                emp.getId(), period.getId(),
                emp.getSueldoBase(), BigDecimal.valueOf(500),
                BigDecimal.valueOf(160), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(80_000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(80_000), BigDecimal.ZERO, BigDecimal.valueOf(80_000),
                List.of(), BigDecimal.ZERO, List.of(advanceId), advanceAmount,
                BigDecimal.valueOf(80_000).subtract(advanceAmount)
        );
        Payslip p = PayslipFactory.normal(companyId, emp, period, calc);
        p.setEstado(estado);
        if (estado == EstadoRecibo.PAGADO) p.setFechaPago(LocalDate.of(2026, 6, 30));
        entityManager.persist(p); entityManager.flush();
        return p;
    }

    private Advance createAdvance(Employee employee, LocalDate fecha, BigDecimal monto, EstadoAdelanto estado) {
        Advance a = new Advance();
        a.setCompanyId(employee.getCompanyId());
        a.setEmployee(employee);
        a.setFecha(fecha);
        a.setMonto(monto);
        a.setMotivo("Adelanto test");
        a.setEstado(estado);
        entityManager.persist(a); entityManager.flush();
        return a;
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol,
                                      Employee employee) throws Exception {
        User user = new User();
        user.setCompanyId(companyId); user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(rol); user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(false);
        if (employee != null) user.setEmployee(employee);
        userRepository.save(user); entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String resp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("accessToken").asText();
    }
}
