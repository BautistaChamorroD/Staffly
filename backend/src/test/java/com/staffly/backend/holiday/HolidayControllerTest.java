// backend/src/test/java/com/staffly/backend/holiday/HolidayControllerTest.java
package com.staffly.backend.holiday;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HolidayControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/holidays";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private UUID companyBId;
    private Branch branchA1;
    private Branch branchA2;
    private String adminAToken;

    @BeforeEach
    void seedTwoCompanies() throws Exception {
        companyAId = createCompany("Empresa A");
        branchA1 = createBranch(companyAId, "Sucursal A1");
        branchA2 = createBranch(companyAId, "Sucursal A2");
        adminAToken = createUserAndLogin(companyAId, "admin-a@empresa-a.com", RolUsuario.ADMIN, null);
        companyBId = createCompany("Empresa B");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private Branch createBranch(UUID companyId, String nombre) {
        Branch branch = new Branch();
        branch.setCompanyId(companyId);
        branch.setNombre(nombre);
        branch.setDireccion("Dirección");
        branch.setZonaHoraria("America/Argentina/Buenos_Aires");
        branch.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(branch);
        entityManager.flush();
        return branch;
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol, Branch assignedBranch)
            throws Exception {
        User user = new User();
        user.setCompanyId(companyId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(rol);
        user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(false);
        if (assignedBranch != null) user.getBranches().add(assignedBranch);
        userRepository.save(user);
        entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }

    private String createHoliday(String token, String fecha, String nombre, UUID branchId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fecha", fecha);
        body.put("nombre", nombre);
        body.put("recurrente", false);
        if (branchId != null) body.put("branchId", branchId.toString());

        String response = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void adminCrudLifecycle() throws Exception {
        // crear y verificar respuesta
        String holidayId = createHoliday(adminAToken, "2026-12-25", "Navidad", null);

        // listar — orden por fecha ASC
        createHoliday(adminAToken, "2026-01-01", "Año Nuevo", null);
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fecha").value("2026-01-01"))
                .andExpect(jsonPath("$[1].fecha").value("2026-12-25"));

        // PATCH parcial: solo nombre y recurrente
        mockMvc.perform(patch(BASE_URL + "/" + holidayId)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Navidad Actualizada", "recurrente", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Navidad Actualizada"))
                .andExpect(jsonPath("$.recurrente").value(true))
                .andExpect(jsonPath("$.fecha").value("2026-12-25"));

        // DELETE
        mockMvc.perform(delete(BASE_URL + "/" + holidayId)
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void adminCreatesGlobalAndBranchSpecificHoliday() throws Exception {
        // global (sin branchId) — branchId viene null en el JSON
        String globalId = createHoliday(adminAToken, "2026-12-25", "Navidad", null);
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchId").value(org.hamcrest.Matchers.nullValue()));

        // específico de sucursal
        String branchSpecificId = createHoliday(adminAToken, "2026-07-09", "Día de la Independencia", branchA1.getId());
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].branchId").value(branchA1.getId().toString()));
    }

    @Test
    void rrhhCanListButNotWrite() throws Exception {
        String rrhhToken = createUserAndLogin(companyAId, "rrhh@empresa-a.com", RolUsuario.RRHH, null);
        createHoliday(adminAToken, "2026-12-25", "Navidad", null);

        // RRHH puede listar
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + rrhhToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // RRHH no puede crear
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + rrhhToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-01-01", "nombre", "Año Nuevo", "recurrente", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorScopedVisibility() throws Exception {
        String supervisorToken = createUserAndLogin(companyAId, "supervisor@empresa-a.com",
                RolUsuario.SUPERVISOR, branchA1);

        // admin crea: 1 global, 1 para branchA1, 1 para branchA2
        createHoliday(adminAToken, "2026-12-25", "Navidad", null);
        createHoliday(adminAToken, "2026-07-09", "Feriado A1", branchA1.getId());
        createHoliday(adminAToken, "2026-05-01", "Feriado A2", branchA2.getId());

        // supervisor ve global + branchA1, no ve branchA2
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // supervisor filtra por ?branchId= de su sucursal → global + branchA1
        mockMvc.perform(get(BASE_URL + "?branchId=" + branchA1.getId())
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void supervisorCannotFilterByOutOfScopeBranch() throws Exception {
        String supervisorToken = createUserAndLogin(companyAId, "supervisor@empresa-a.com",
                RolUsuario.SUPERVISOR, branchA1);

        // branchA2 no está en el alcance del supervisor
        mockMvc.perform(get(BASE_URL + "?branchId=" + branchA2.getId())
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeHasNoAccess() throws Exception {
        // Crear un usuario EMPLOYEE sin employee vinculado
        String employeeToken = createUserAndLogin(companyAId, "empleado@empresa-a.com",
                RolUsuario.EMPLOYEE, null);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25", "nombre", "Navidad", "recurrente", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateValidations() throws Exception {
        // Duplicado global mismo día → 409
        createHoliday(adminAToken, "2026-12-25", "Navidad", null);
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25", "nombre", "Navidad 2", "recurrente", false))))
                .andExpect(status().isConflict());

        // Duplicado mismo branch mismo día → 409
        createHoliday(adminAToken, "2026-07-09", "Feriado A1", branchA1.getId());
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-07-09", "nombre", "Feriado A1 bis",
                                "recurrente", false, "branchId", branchA1.getId().toString()))))
                .andExpect(status().isConflict());

        // Global + específico mismo día → 201 (ámbitos distintos)
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25", "nombre", "Feriado branchA1 en Navidad",
                                "recurrente", false, "branchId", branchA1.getId().toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void branchIdNotFoundReturns404() throws Exception {
        UUID unknownBranchId = UUID.randomUUID();
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25", "nombre", "Navidad",
                                "recurrente", false, "branchId", unknownBranchId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantIsolation() throws Exception {
        // holiday de empresa B: admin de A no debe verlo (lo crea directo en la BD)
        Branch branchB1 = createBranch(companyBId, "Sucursal B1");
        Holiday holidayB = new Holiday();
        holidayB.setCompanyId(companyBId);
        holidayB.setFecha(java.time.LocalDate.of(2026, 12, 25));
        holidayB.setNombre("Navidad B");
        holidayB.setRecurrente(false);
        entityManager.persist(holidayB);
        entityManager.flush();

        // admin A no ve el holiday de empresa B en el listado
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // PATCH sobre el holiday de empresa B → 404
        mockMvc.perform(patch(BASE_URL + "/" + holidayB.getId())
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Intento"))))
                .andExpect(status().isNotFound());

        // branchId de empresa B en create para empresa A → 404
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-07-09", "nombre", "Feriado",
                                "recurrente", false, "branchId", branchB1.getId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anioFilterWorksCorrectly() throws Exception {
        createHoliday(adminAToken, "2025-12-25", "Navidad 2025", null);
        createHoliday(adminAToken, "2026-07-09", "Independencia 2026", null);

        // solo muestra los de 2026
        mockMvc.perform(get(BASE_URL + "?anio=2026").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fecha").value("2026-07-09"));

        // solo muestra los de 2025
        mockMvc.perform(get(BASE_URL + "?anio=2025").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fecha").value("2025-12-25"));
    }

    @Test
    void patchSelfExclusionNoDuplicateError() throws Exception {
        // Crear un feriado
        String holidayId = createHoliday(adminAToken, "2026-12-25", "Navidad", null);

        // PATCH con la misma fecha → NO debe 409 (se excluye a sí mismo)
        mockMvc.perform(patch(BASE_URL + "/" + holidayId)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Navidad Actualizada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Navidad Actualizada"));
    }
}
