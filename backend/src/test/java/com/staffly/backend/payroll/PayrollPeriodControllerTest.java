package com.staffly.backend.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayrollPeriodControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/payroll-periods";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private String adminToken;
    private String rrhhToken;
    private String supervisorToken;

    @BeforeEach
    void setUp() throws Exception {
        companyAId = createCompany("Empresa A");
        adminToken      = createUserAndLogin(companyAId, "admin@a.com",      RolUsuario.ADMIN);
        rrhhToken       = createUserAndLogin(companyAId, "rrhh@a.com",       RolUsuario.RRHH);
        supervisorToken = createUserAndLogin(companyAId, "supervisor@a.com", RolUsuario.SUPERVISOR);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void adminCreatesPeriodAndListsIt() throws Exception {
        String periodJson = createPeriod(adminToken, "2026-08-01", "2026-08-31");
        String periodId = objectMapper.readTree(periodJson).get("id").asText();

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("ABIERTO"))
                .andExpect(jsonPath("$[0].fechaInicio").value("2026-08-01"))
                .andExpect(jsonPath("$[0].fechaFin").value("2026-08-31"))
                .andExpect(jsonPath("$[0].fechaCierre").doesNotExist());

        mockMvc.perform(get(BASE_URL + "/" + periodId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(periodId));
    }

    @Test
    void rrhhCanCreateAndListPeriods() throws Exception {
        createPeriod(rrhhToken, "2026-09-01", "2026-09-30");

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + rrhhToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void supervisorCannotAccessPeriods() throws Exception {
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("fechaInicio", "2026-08-01", "fechaFin", "2026-08-31"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotCreateWhenOpenPeriodExists() throws Exception {
        createPeriod(adminToken, "2026-08-01", "2026-08-31");

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("fechaInicio", "2026-09-01", "fechaFin", "2026-09-30"))))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotCreateWhenReOpenedPeriodExists() throws Exception {
        PayrollPeriod reopen = new PayrollPeriod();
        reopen.setCompanyId(companyAId);
        reopen.setFechaInicio(LocalDate.of(2026, 7, 1));
        reopen.setFechaFin(LocalDate.of(2026, 7, 31));
        reopen.setEstado(EstadoPeriodo.REABIERTO);
        entityManager.persist(reopen);
        entityManager.flush();

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("fechaInicio", "2026-08-01", "fechaFin", "2026-08-31"))))
                .andExpect(status().isConflict());
    }

    @Test
    void fechaFinMustBeAfterFechaInicio() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("fechaInicio", "2026-08-31", "fechaFin", "2026-08-01"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filterByEstado() throws Exception {
        createPeriod(adminToken, "2026-08-01", "2026-08-31");

        PayrollPeriod closed = new PayrollPeriod();
        closed.setCompanyId(companyAId);
        closed.setFechaInicio(LocalDate.of(2026, 7, 1));
        closed.setFechaFin(LocalDate.of(2026, 7, 31));
        closed.setEstado(EstadoPeriodo.CERRADO);
        closed.setFechaCierre(LocalDate.of(2026, 8, 1));
        entityManager.persist(closed);
        entityManager.flush();

        mockMvc.perform(get(BASE_URL).param("estado", "ABIERTO")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("ABIERTO"));

        mockMvc.perform(get(BASE_URL).param("estado", "CERRADO")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("CERRADO"));
    }

    @Test
    void getByIdReturns404ForOtherTenant() throws Exception {
        createPeriod(adminToken, "2026-08-01", "2026-08-31");

        UUID companyBId = createCompany("Empresa B");
        String adminBToken = createUserAndLogin(companyBId, "admin@b.com", RolUsuario.ADMIN);
        String periodBJson = createPeriod(adminBToken, "2026-08-01", "2026-08-31");
        String periodBId = objectMapper.readTree(periodBJson).get("id").asText();

        mockMvc.perform(get(BASE_URL + "/" + periodBId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listOrderedByFechaInicioDesc() throws Exception {
        PayrollPeriod old = new PayrollPeriod();
        old.setCompanyId(companyAId);
        old.setFechaInicio(LocalDate.of(2026, 6, 1));
        old.setFechaFin(LocalDate.of(2026, 6, 30));
        old.setEstado(EstadoPeriodo.CERRADO);
        old.setFechaCierre(LocalDate.of(2026, 7, 1));
        entityManager.persist(old);
        entityManager.flush();

        createPeriod(adminToken, "2026-08-01", "2026-08-31");

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fechaInicio").value("2026-08-01"))
                .andExpect(jsonPath("$[1].fechaInicio").value("2026-06-01"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String createPeriod(String token, String inicio, String fin) throws Exception {
        return mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("fechaInicio", inicio, "fechaFin", fin))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID createCompany(String nombre) {
        Company company = new Company();
        company.setNombre(nombre);
        company.setRazonSocial(nombre + " SRL");
        company.setPais("AR");
        company.setMoneda("ARS");
        company.setZonaHoraria("America/Argentina/Buenos_Aires");
        company.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(company);
        entityManager.flush();
        return company.getId();
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol) throws Exception {
        User user = new User();
        user.setCompanyId(companyId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(rol);
        user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(false);
        userRepository.save(user);
        entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }
}
