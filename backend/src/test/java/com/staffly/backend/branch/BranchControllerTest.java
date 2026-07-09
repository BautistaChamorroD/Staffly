package com.staffly.backend.branch;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BranchControllerTest {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String companyAToken;
    private String companyBToken;

    @BeforeEach
    void seedTwoCompaniesWithAdmins() throws Exception {
        companyAToken = createCompanyAdminAndLogin("admin-a@empresa-a.com", "Empresa A");
        companyBToken = createCompanyAdminAndLogin("admin-b@empresa-b.com", "Empresa B");
    }

    private String createCompanyAdminAndLogin(String email, String companyName) throws Exception {
        Company company = new Company();
        company.setNombre(companyName);
        company.setRazonSocial(companyName + " SRL");
        company.setPais("AR");
        company.setMoneda("ARS");
        company.setZonaHoraria("America/Argentina/Buenos_Aires");
        company.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(company);

        User admin = new User();
        admin.setCompanyId(company.getId());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRol(RolUsuario.ADMIN);
        admin.setEstado(EstadoUsuario.ACTIVO);
        admin.setDebeCambiarPassword(false);
        userRepository.save(admin);
        entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }

    private String createBranchRequestBody(String nombre) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "nombre", nombre,
                "direccion", "Av. Siempre Viva 123",
                "zonaHoraria", "America/Argentina/Buenos_Aires"));
    }

    @Test
    void createsBranchScopedToCallerCompany() throws Exception {
        String body = createBranchRequestBody("Sucursal Centro");

        mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Sucursal Centro"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @Test
    void tenantIsolation_companyDoesNotSeeOtherCompanyBranches() throws Exception {
        String bodyA = createBranchRequestBody("Sucursal A");
        String createResponseA = mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(bodyA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String branchAId = objectMapper.readTree(createResponseA).get("id").asText();

        String bodyB = createBranchRequestBody("Sucursal B");
        mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + companyBToken)
                        .contentType("application/json")
                        .content(bodyB))
                .andExpect(status().isCreated());

        // company A solo ve su propia sucursal en el listado
        mockMvc.perform(get("/api/v1/branches")
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Sucursal A"));

        // company B no puede ver el detalle de la sucursal de company A -> 404, no 403
        mockMvc.perform(get("/api/v1/branches/" + branchAId)
                        .header("Authorization", "Bearer " + companyBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // company B tampoco puede editarla
        String updateBody = objectMapper.writeValueAsString(Map.of("nombre", "Hackeada"));
        mockMvc.perform(patch("/api/v1/branches/" + branchAId)
                        .header("Authorization", "Bearer " + companyBToken)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUnknownBranchReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/branches/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAndUpdateStatusLifecycle() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(createBranchRequestBody("Sucursal Original")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String branchId = objectMapper.readTree(createResponse).get("id").asText();

        String updateBody = objectMapper.writeValueAsString(Map.of("nombre", "Sucursal Renombrada"));
        mockMvc.perform(patch("/api/v1/branches/" + branchId)
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Sucursal Renombrada"))
                .andExpect(jsonPath("$.direccion").value("Av. Siempre Viva 123"));

        String statusBody = objectMapper.writeValueAsString(Map.of("estado", "INACTIVA"));
        mockMvc.perform(patch("/api/v1/branches/" + branchId + "/status")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(statusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVA"));
    }

    @Test
    void createWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/branches")
                        .contentType("application/json")
                        .content(createBranchRequestBody("Sin Auth")))
                .andExpect(status().isUnauthorized());
    }
}
