package com.staffly.backend.payroll;

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
import com.staffly.backend.payslip.EstadoRecibo;
import com.staffly.backend.payslip.Payslip;
import com.staffly.backend.payslip.PayslipRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayrollPeriodCloseControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/payroll-periods";

    @Autowired private MockMvc            mockMvc;
    @Autowired private ObjectMapper       objectMapper;
    @Autowired private EntityManager      entityManager;
    @Autowired private UserRepository     userRepository;
    @Autowired private PasswordEncoder    passwordEncoder;
    @Autowired private PayslipRepository  payslipRepository;
    @Autowired private PayrollConfigRepository configRepository;

    private UUID        companyAId;
    private Branch      branchA;
    private Employee    empA1;
    private Employee    empA2;
    private PayrollPeriod periodA;
    private String      adminAToken;
    private String      rrhhAToken;

    @BeforeEach
    void seed() throws Exception {
        companyAId  = createCompany("Empresa A");
        branchA     = createBranch(companyAId, "Sucursal A");
        empA1       = createEmployee(companyAId, branchA, "Juan",  "Pérez",   EstadoLaboral.ACTIVO,    EstadoLiquidacion.AL_DIA);
        empA2       = createEmployee(companyAId, branchA, "Ana",   "García",  EstadoLaboral.ACTIVO,    EstadoLiquidacion.AL_DIA);
        periodA     = createPeriod(companyAId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        createPayrollConfig(companyAId);
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN, null);
        rrhhAToken  = createUserAndLogin(companyAId, "rrhh@a.com",  RolUsuario.RRHH,  null);
    }

    @Test
    void closeGeneratesPayslipsForAllActiveEmployees() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"))
                .andExpect(jsonPath("$.fechaCierre").isNotEmpty());

        entityManager.flush();
        entityManager.clear();

        List<Payslip> payslips = payslipRepository.findByCompanyId(companyAId);
        assertThat(payslips)
                .hasSize(2)
                .allSatisfy(p -> {
                    assertThat(p.getEstado()).isEqualTo(EstadoRecibo.GENERADO);
                    assertThat(p.getFechaPago()).isNull();
                });
    }

    @Test
    void generatedPayslipCanBeMarkedPaidAfterClose() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        Payslip payslip = payslipRepository.findByCompanyId(companyAId).stream()
                .filter(p -> p.getEmployeeId().equals(empA1.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(patch("/api/v1/payslips/" + payslip.getId() + "/mark-paid")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADO"))
                .andExpect(jsonPath("$.fechaPago").isNotEmpty());

        mockMvc.perform(patch("/api/v1/payslips/" + payslip.getId() + "/mark-paid")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeDiscountsAndMarksAdvances() throws Exception {
        Advance adv = createAdvance(companyAId, empA1, BigDecimal.valueOf(5000));
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        Advance refreshed = entityManager.find(Advance.class, adv.getId());
        assertThat(refreshed.getEstado()).isEqualTo(EstadoAdelanto.DESCONTADO);
        assertThat(refreshed.getPayrollPeriodId()).isEqualTo(periodA.getId());
    }

    @Test
    void closeDoesNotDiscountFutureAdvances() throws Exception {
        Advance inPeriod = createAdvance(companyAId, empA1, LocalDate.of(2026, 7, 10), BigDecimal.valueOf(5000));
        Advance future = createAdvance(companyAId, empA1, LocalDate.of(2026, 8, 1), BigDecimal.valueOf(3000));
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        Advance refreshedInPeriod = entityManager.find(Advance.class, inPeriod.getId());
        Advance refreshedFuture = entityManager.find(Advance.class, future.getId());

        assertThat(refreshedInPeriod.getEstado()).isEqualTo(EstadoAdelanto.DESCONTADO);
        assertThat(refreshedInPeriod.getPayrollPeriodId()).isEqualTo(periodA.getId());
        assertThat(refreshedFuture.getEstado()).isEqualTo(EstadoAdelanto.PENDIENTE);
        assertThat(refreshedFuture.getPayrollPeriodId()).isNull();

        List<UUID> applied = payslipRepository
                .findNormalByCompanyIdAndEmployeeIdAndPayrollPeriodId(companyAId, empA1.getId(), periodA.getId())
                .orElseThrow()
                .getAdelantosAplicados();
        assertThat(applied).containsExactly(inPeriod.getId());
    }

    @Test
    void closeIncludesTerminatedEmployeeWithPendingLiquidation() throws Exception {
        Employee terminated = createEmployee(companyAId, branchA, "Ex", "Empleado",
                EstadoLaboral.BAJA, EstadoLiquidacion.PENDIENTE);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        long count = payslipRepository.findByCompanyId(companyAId).size();
        assertThat(count).isEqualTo(3); // empA1 + empA2 + terminated
    }

    @Test
    void closeIncludesEmployeeTerminatedThroughStatusEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/" + empA1.getId() + "/status")
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estadoLaboral", "BAJA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoLaboral").value("BAJA"))
                .andExpect(jsonPath("$.estadoLiquidacion").value("PENDIENTE"));

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        long count = payslipRepository.findByCompanyId(companyAId).size();
        assertThat(count).isEqualTo(2); // empA1 (BAJA + PENDIENTE) + empA2 activo
    }

    @Test
    void closeAlreadyClosedPeriodReturns409() throws Exception {
        periodA.setEstado(EstadoPeriodo.CERRADO);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isConflict());
    }

    @Test
    void rrhhCannotClosePeriod() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void reabrirYCerrarDeNuevo_noDuplicaPayslips() throws Exception {
        // issue #147 (AUD-26, parte b): cerrar → reabrir → volver a cerrar el mismo
        // período no debe dejar dos recibos NORMAL para el mismo empleado+período.
        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        long countTrasPrimerCierre = payslipRepository.findByCompanyId(companyAId).size();

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/reopen")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        long countTrasSegundoCierre = payslipRepository.findByCompanyId(companyAId).size();

        assertThat(countTrasSegundoCierre).isEqualTo(countTrasPrimerCierre);
    }

    @Test
    void tenantIsolation() throws Exception {
        UUID companyBId   = createCompany("Empresa B");
        String adminBToken = createUserAndLogin(companyBId, "admin@b.com", RolUsuario.ADMIN, null);

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/close")
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

    private Employee createEmployee(UUID companyId, Branch branch, String nombre, String apellido,
                                    EstadoLaboral estadoLaboral, EstadoLiquidacion estadoLiquidacion) {
        Employee e = new Employee();
        e.setCompanyId(companyId); e.setNombre(nombre); e.setApellido(apellido);
        e.setDocumento(UUID.randomUUID().toString().substring(0, 8));
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setSueldoBase(BigDecimal.valueOf(100_000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(estadoLaboral);
        e.setEstadoLiquidacion(estadoLiquidacion);
        e.getBranches().add(branch);
        entityManager.persist(e); entityManager.flush();
        return e;
    }

    private PayrollPeriod createPeriod(UUID companyId, LocalDate ini, LocalDate fin) {
        PayrollPeriod pp = new PayrollPeriod();
        pp.setCompanyId(companyId);
        pp.setFechaInicio(ini); pp.setFechaFin(fin);
        pp.setEstado(EstadoPeriodo.ABIERTO);
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

    private Advance createAdvance(UUID companyId, Employee emp, BigDecimal monto) {
        return createAdvance(companyId, emp, LocalDate.of(2026, 7, 10), monto);
    }

    private Advance createAdvance(UUID companyId, Employee emp, LocalDate fecha, BigDecimal monto) {
        Advance a = new Advance();
        a.setCompanyId(companyId); a.setEmployee(emp);
        a.setFecha(fecha);
        a.setMonto(monto); a.setMotivo("Adelanto");
        a.setEstado(EstadoAdelanto.PENDIENTE);
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
