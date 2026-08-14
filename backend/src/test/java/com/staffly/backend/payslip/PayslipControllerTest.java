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
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payroll.EstadoPeriodo;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayslipControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/payslips";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private Branch branchA;
    private Employee empA1;
    private Employee empA2;
    private PayrollPeriod periodA;
    private String adminAToken;
    private String rrhhAToken;

    @BeforeEach
    void seed() throws Exception {
        companyAId = createCompany("Empresa A");
        branchA   = createBranch(companyAId, "Sucursal A");
        empA1     = createEmployee(companyAId, branchA, "Juan", "Pérez");
        empA2     = createEmployee(companyAId, branchA, "Ana",  "García");
        periodA   = createPeriod(companyAId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN, null);
        rrhhAToken  = createUserAndLogin(companyAId, "rrhh@a.com",  RolUsuario.RRHH,  null);
    }

    // ── GET /payslips ─────────────────────────────────────────────────────────

    @Test
    void listReturnsAllForAdmin() throws Exception {
        createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);
        createPayslip(companyAId, empA2, periodA, EstadoRecibo.PAGADO);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listFilteredByEmployee() throws Exception {
        createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);
        createPayslip(companyAId, empA2, periodA, EstadoRecibo.GENERADO);

        mockMvc.perform(get(BASE_URL + "?employeeId=" + empA1.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(empA1.getId().toString()));
    }

    @Test
    void listFilteredByEstado() throws Exception {
        createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);
        createPayslip(companyAId, empA2, periodA, EstadoRecibo.PAGADO);

        mockMvc.perform(get(BASE_URL + "?estado=GENERADO")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("GENERADO"));
    }

    @Test
    void employeeSeesOnlyOwnPayslips() throws Exception {
        createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);
        createPayslip(companyAId, empA2, periodA, EstadoRecibo.GENERADO);

        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(empA1.getId().toString()));
    }

    @Test
    void getMeEndpointWorksForEmployee() throws Exception {
        createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);

        mockMvc.perform(get(BASE_URL + "/me").header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── GET /payslips/{id} ────────────────────────────────────────────────────

    @Test
    void adminCanGetById() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);

        mockMvc.perform(get(BASE_URL + "/" + p.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p.getId().toString()))
                .andExpect(jsonPath("$.netoFinal").exists())
                .andExpect(jsonPath("$.detalleDescuentos").isArray());
    }

    @Test
    void employeeCanGetOwnById() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);
        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);

        mockMvc.perform(get(BASE_URL + "/" + p.getId())
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p.getId().toString()));
    }

    @Test
    void employeeCannotGetOtherPayslip() throws Exception {
        Payslip p = createPayslip(companyAId, empA2, periodA, EstadoRecibo.GENERADO);
        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);

        mockMvc.perform(get(BASE_URL + "/" + p.getId())
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /payslips/{id}/mark-paid ────────────────────────────────────────

    @Test
    void markPaidTransitionsToGeneradoPagado() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);

        mockMvc.perform(patch(BASE_URL + "/" + p.getId() + "/mark-paid")
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("fechaPago", "2026-08-31"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADO"))
                .andExpect(jsonPath("$.fechaPago").value("2026-08-31"));
    }

    @Test
    void markPaidOnAlreadyPaidReturns400() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);

        mockMvc.perform(patch(BASE_URL + "/" + p.getId() + "/mark-paid")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markPaidOnAnuladoReturns400() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.ANULADO);

        mockMvc.perform(patch(BASE_URL + "/" + p.getId() + "/mark-paid")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isBadRequest());
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    void tenantIsolation() throws Exception {
        createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);

        UUID companyBId = createCompany("Empresa B");
        String adminBToken = createUserAndLogin(companyBId, "admin@b.com", RolUsuario.ADMIN, null);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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
        pp.setFechaInicio(ini);
        pp.setFechaFin(fin);
        pp.setEstado(EstadoPeriodo.ABIERTO);
        entityManager.persist(pp); entityManager.flush();
        return pp;
    }

    private Payslip createPayslip(UUID companyId, Employee emp, PayrollPeriod period,
                                   EstadoRecibo estado) {
        PayslipCalculation calc = new PayslipCalculation(
                emp.getId(), period.getId(),
                emp.getSueldoBase(), BigDecimal.valueOf(500),
                BigDecimal.valueOf(160), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(80_000), BigDecimal.ZERO, BigDecimal.valueOf(80_000),
                List.of(), BigDecimal.ZERO,
                List.of(), BigDecimal.ZERO,
                BigDecimal.valueOf(80_000)
        );
        Payslip p = PayslipFactory.normal(companyId, emp, period, calc);
        p.setEstado(estado);
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
