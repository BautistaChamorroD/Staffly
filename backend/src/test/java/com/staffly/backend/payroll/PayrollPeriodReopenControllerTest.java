package com.staffly.backend.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.staffly.backend.payslip.PayslipFactory;
import com.staffly.backend.payslip.PayslipRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayrollPeriodReopenControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL  = "/api/v1/payroll-periods";

    @Autowired private MockMvc           mockMvc;
    @Autowired private ObjectMapper      objectMapper;
    @Autowired private EntityManager     entityManager;
    @Autowired private UserRepository    userRepository;
    @Autowired private PasswordEncoder   passwordEncoder;
    @Autowired private PayslipRepository payslipRepository;

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
        periodA     = createPeriod(companyAId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), EstadoPeriodo.CERRADO);
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN, null);
        rrhhAToken  = createUserAndLogin(companyAId, "rrhh@a.com",  RolUsuario.RRHH,  null);
    }

    @Test
    void reopenChangesEstadoToReabierto() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/reopen")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REABIERTO"))
                .andExpect(jsonPath("$.fechaCierre").doesNotExist());
    }

    @Test
    void reopenResetsPayslipsToGenerado() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/reopen")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        Payslip refreshed = entityManager.find(Payslip.class, p.getId());
        assertThat(refreshed.getEstado()).isEqualTo(EstadoRecibo.GENERADO);
        assertThat(refreshed.getFechaPago()).isNull();
    }

    @Test
    void reopenAlreadyOpenPeriodReturns409() throws Exception {
        periodA.setEstado(EstadoPeriodo.ABIERTO);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/reopen")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isConflict());
    }

    @Test
    void reopenWithSubsequentClosedPeriodReturns422() throws Exception {
        createPeriod(companyAId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), EstadoPeriodo.CERRADO);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/reopen")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rrhhCannotReopenPeriod() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/reopen")
                        .header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantIsolation() throws Exception {
        UUID companyBId    = createCompany("Empresa B");
        String adminBToken = createUserAndLogin(companyBId, "admin@b.com", RolUsuario.ADMIN, null);

        mockMvc.perform(post(BASE_URL + "/" + periodA.getId() + "/reopen")
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

    private PayrollPeriod createPeriod(UUID companyId, LocalDate ini, LocalDate fin, EstadoPeriodo estado) {
        PayrollPeriod pp = new PayrollPeriod();
        pp.setCompanyId(companyId);
        pp.setFechaInicio(ini); pp.setFechaFin(fin);
        pp.setEstado(estado);
        if (estado == EstadoPeriodo.CERRADO) pp.setFechaCierre(fin);
        entityManager.persist(pp); entityManager.flush();
        return pp;
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
        p.setFechaPago(LocalDate.of(2026, 6, 30));
        entityManager.persist(p); entityManager.flush();
        return p;
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
