package com.staffly.backend.advance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
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
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdvanceControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/advances";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private Branch branchA;
    private Employee empA1;
    private Employee empA2;
    private String adminAToken;
    private String rrhhAToken;

    @BeforeEach
    void seed() throws Exception {
        companyAId = createCompany("Empresa A");
        branchA   = createBranch(companyAId, "Sucursal A");
        empA1     = createEmployee(companyAId, branchA, "Juan", "Pérez");
        empA2     = createEmployee(companyAId, branchA, "Ana",  "García");
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN, null);
        rrhhAToken  = createUserAndLogin(companyAId, "rrhh@a.com",  RolUsuario.RRHH,  null);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void adminCanCreateAdvance() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(buildCreateBody(empA1.getId(), "2026-08-01", "1500.00", "Pago anticipado")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.employeeId").value(empA1.getId().toString()))
                .andExpect(jsonPath("$.monto").value(1500.00))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void rrhhCanCreateAdvance() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + rrhhAToken)
                        .contentType("application/json")
                        .content(buildCreateBody(empA1.getId(), "2026-08-01", "2000.00", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void listReturnsAllCompanyAdvances() throws Exception {
        createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);
        createAdvance(empA2, LocalDate.of(2026, 8, 2), new BigDecimal("500.00"),  EstadoAdelanto.PENDIENTE);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listFilteredByEmployee() throws Exception {
        createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);
        createAdvance(empA2, LocalDate.of(2026, 8, 2), new BigDecimal("500.00"),  EstadoAdelanto.PENDIENTE);

        mockMvc.perform(get(BASE_URL + "?employeeId=" + empA1.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(empA1.getId().toString()));
    }

    @Test
    void listFilteredByEstado() throws Exception {
        createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);
        createAdvance(empA2, LocalDate.of(2026, 8, 2), new BigDecimal("500.00"),  EstadoAdelanto.DESCONTADO);

        mockMvc.perform(get(BASE_URL + "?estado=PENDIENTE")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
    }

    @Test
    void employeeSeesOwnAdvancesOnly() throws Exception {
        createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);
        createAdvance(empA2, LocalDate.of(2026, 8, 2), new BigDecimal("500.00"),  EstadoAdelanto.PENDIENTE);

        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(empA1.getId().toString()));
    }

    @Test
    void adminCanGetById() throws Exception {
        Advance advance = createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);

        mockMvc.perform(get(BASE_URL + "/" + advance.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(advance.getId().toString()));
    }

    @Test
    void employeeCanGetOwnById() throws Exception {
        Advance advance = createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);
        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);

        mockMvc.perform(get(BASE_URL + "/" + advance.getId())
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(advance.getId().toString()));
    }

    @Test
    void employeeCannotGetOtherEmployeeAdvance() throws Exception {
        Advance advance = createAdvance(empA2, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);
        String empToken = createUserAndLogin(companyAId, "emp@a.com", RolUsuario.EMPLOYEE, empA1);

        mockMvc.perform(get(BASE_URL + "/" + advance.getId())
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWorksOnPendiente() throws Exception {
        Advance advance = createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);

        mockMvc.perform(delete(BASE_URL + "/" + advance.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteOnDescontadoReturns409() throws Exception {
        Advance advance = createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.DESCONTADO);

        mockMvc.perform(delete(BASE_URL + "/" + advance.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isConflict());
    }

    @Test
    void tenantIsolation() throws Exception {
        createAdvance(empA1, LocalDate.of(2026, 8, 1), new BigDecimal("1000.00"), EstadoAdelanto.PENDIENTE);

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
        e.setSueldoBase(BigDecimal.valueOf(100000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        e.getBranches().add(branch);
        entityManager.persist(e); entityManager.flush();
        return e;
    }

    private Advance createAdvance(Employee employee, LocalDate fecha, BigDecimal monto, EstadoAdelanto estado) {
        Advance a = new Advance();
        a.setCompanyId(employee.getCompanyId());
        a.setEmployee(employee);
        a.setFecha(fecha);
        a.setMonto(monto);
        a.setMotivo("Test adelanto");
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

    private String buildCreateBody(UUID employeeId, String fecha, String monto, String motivo) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("employeeId", employeeId.toString());
        body.put("fecha", fecha);
        body.put("monto", monto);
        if (motivo != null) body.put("motivo", motivo);
        return objectMapper.writeValueAsString(body);
    }
}
