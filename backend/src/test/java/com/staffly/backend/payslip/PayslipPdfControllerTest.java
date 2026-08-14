package com.staffly.backend.payslip;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayslipPdfControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL  = "/api/v1/payslips";

    @Autowired private MockMvc         mockMvc;
    @Autowired private ObjectMapper    objectMapper;
    @Autowired private EntityManager   entityManager;
    @Autowired private UserRepository  userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID          companyAId;
    private Branch        branchA;
    private Employee      empA1;
    private Employee      empA2;
    private PayrollPeriod periodA;
    private String        adminAToken;
    private String        rrhhAToken;

    @BeforeEach
    void seed() throws Exception {
        companyAId  = createCompany("Heladería La Polar", "La Polar S.R.L.");
        branchA     = createBranch(companyAId, "Sucursal A");
        empA1       = createEmployee(companyAId, branchA, "Juan",  "Pérez");
        empA2       = createEmployee(companyAId, branchA, "Ana",   "García");
        periodA     = createPeriod(companyAId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN, null);
        rrhhAToken  = createUserAndLogin(companyAId, "rrhh@a.com",  RolUsuario.RRHH,  null);
    }

    @Test
    void adminCanDownloadPdf() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        entityManager.flush();

        byte[] body = mockMvc.perform(get(BASE_URL + "/" + p.getId() + "/pdf")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=recibo-" + p.getId() + ".pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isNotEmpty();
        // PDF magic bytes: %PDF
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void rrhhCanDownloadPdf() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.GENERADO);
        entityManager.flush();

        mockMvc.perform(get(BASE_URL + "/" + p.getId() + "/pdf")
                        .header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void employeeCanDownloadOwnPdf() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);
        entityManager.flush();

        mockMvc.perform(get(BASE_URL + "/" + p.getId() + "/pdf")
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void employeeCannotDownloadOthersPdf() throws Exception {
        Payslip p = createPayslip(companyAId, empA2, periodA, EstadoRecibo.PAGADO);
        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);
        entityManager.flush();

        mockMvc.perform(get(BASE_URL + "/" + p.getId() + "/pdf")
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantIsolation() throws Exception {
        Payslip p = createPayslip(companyAId, empA1, periodA, EstadoRecibo.PAGADO);
        UUID companyBId    = createCompany("Empresa B", "B SRL");
        String adminBToken = createUserAndLogin(companyBId, "admin@b.com", RolUsuario.ADMIN, null);
        entityManager.flush();

        mockMvc.perform(get(BASE_URL + "/" + p.getId() + "/pdf")
                        .header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID createCompany(String nombre, String razonSocial) {
        Company c = new Company();
        c.setNombre(nombre); c.setRazonSocial(razonSocial);
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
        pp.setEstado(EstadoPeriodo.CERRADO);
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
                List.of(), BigDecimal.ZERO, List.of(), BigDecimal.ZERO,
                BigDecimal.valueOf(80_000)
        );
        Payslip p = PayslipFactory.normal(companyId, emp, period, calc);
        p.setEstado(estado);
        if (estado == EstadoRecibo.PAGADO) p.setFechaPago(LocalDate.of(2026, 6, 30));
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
