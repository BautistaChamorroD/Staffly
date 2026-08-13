package com.staffly.backend.leave;

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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaveTypeControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/leave-types";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private UUID companyBId;
    private String adminAToken;

    @BeforeEach
    void seedTwoCompanies() throws Exception {
        companyAId = createCompany("Empresa A");
        adminAToken = createUserAndLogin(companyAId, "admin-a@empresa-a.com", RolUsuario.ADMIN);
        companyBId = createCompany("Empresa B");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    private String createLeaveType(String token, String nombre, boolean esPaga, Integer cupoAnual) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("nombre", nombre);
        body.put("esPaga", esPaga);
        if (cupoAnual != null) body.put("cupoAnual", cupoAnual);

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
        String id = createLeaveType(adminAToken, "Vacaciones", true, 20);

        // listar — ordenado por nombre ASC
        createLeaveType(adminAToken, "Enfermedad", false, null);
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Enfermedad"))
                .andExpect(jsonPath("$[1].nombre").value("Vacaciones"));

        // PATCH parcial: solo cupoAnual
        mockMvc.perform(patch(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("cupoAnual", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Vacaciones"))
                .andExpect(jsonPath("$.esPaga").value(true))
                .andExpect(jsonPath("$.cupoAnual").value(30));

        // DELETE
        mockMvc.perform(delete(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void leaveTypeWithoutCupoReturnsNullCupoAnual() throws Exception {
        createLeaveType(adminAToken, "Duelo", false, null);
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cupoAnual").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void allAuthorizedRolesCanList() throws Exception {
        createLeaveType(adminAToken, "Vacaciones", true, null);

        String rrhhToken = createUserAndLogin(companyAId, "rrhh@empresa-a.com", RolUsuario.RRHH);
        String supervisorToken = createUserAndLogin(companyAId, "supervisor@empresa-a.com", RolUsuario.SUPERVISOR);
        String employeeToken = createUserAndLogin(companyAId, "empleado@empresa-a.com", RolUsuario.EMPLOYEE);

        for (String token : new String[]{rrhhToken, supervisorToken, employeeToken}) {
            mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Test
    void onlyAdminCanWrite() throws Exception {
        String rrhhToken = createUserAndLogin(companyAId, "rrhh@empresa-a.com", RolUsuario.RRHH);
        String supervisorToken = createUserAndLogin(companyAId, "supervisor@empresa-a.com", RolUsuario.SUPERVISOR);
        String employeeToken = createUserAndLogin(companyAId, "empleado@empresa-a.com", RolUsuario.EMPLOYEE);

        String body = objectMapper.writeValueAsString(Map.of("nombre", "Vacaciones", "esPaga", true));

        for (String token : new String[]{rrhhToken, supervisorToken, employeeToken}) {
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void duplicateNombreReturns409() throws Exception {
        createLeaveType(adminAToken, "Vacaciones", true, null);

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Vacaciones", "esPaga", false))))
                .andExpect(status().isConflict());
    }

    @Test
    void patchSelfExclusionNoDuplicateError() throws Exception {
        String id = createLeaveType(adminAToken, "Vacaciones", true, null);

        mockMvc.perform(patch(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Vacaciones"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Vacaciones"));
    }

    @Test
    void patchDuplicateNombreWithOtherReturns409() throws Exception {
        createLeaveType(adminAToken, "Vacaciones", true, null);
        String id2 = createLeaveType(adminAToken, "Enfermedad", false, null);

        mockMvc.perform(patch(BASE_URL + "/" + id2)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Vacaciones"))))
                .andExpect(status().isConflict());
    }

    @Test
    void validationErrorsMissingFields() throws Exception {
        // falta nombre
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("esPaga", true))))
                .andExpect(status().isBadRequest());

        // falta esPaga
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Vacaciones"))))
                .andExpect(status().isBadRequest());

        // cupoAnual = 0 → inválido (debe ser >= 1)
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("nombre", "Vacaciones", "esPaga", true, "cupoAnual", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFoundReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(patch(BASE_URL + "/" + unknownId)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Algo"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(BASE_URL + "/" + unknownId)
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantIsolation() throws Exception {
        // tipo de empresa B creado directamente en BD
        LeaveType leaveTypeB = new LeaveType();
        leaveTypeB.setCompanyId(companyBId);
        leaveTypeB.setNombre("Vacaciones B");
        leaveTypeB.setEsPaga(true);
        entityManager.persist(leaveTypeB);
        entityManager.flush();

        // admin A no ve los tipos de empresa B
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // PATCH del tipo de empresa B → 404 desde admin A
        mockMvc.perform(patch(BASE_URL + "/" + leaveTypeB.getId())
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Intento"))))
                .andExpect(status().isNotFound());

        // DELETE del tipo de empresa B → 404 desde admin A
        mockMvc.perform(delete(BASE_URL + "/" + leaveTypeB.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }
}
