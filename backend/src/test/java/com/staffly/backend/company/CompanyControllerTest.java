package com.staffly.backend.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.platform.EstadoPlatformAdmin;
import com.staffly.backend.platform.PlatformAdmin;
import com.staffly.backend.platform.PlatformAdminRepository;
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
class CompanyControllerTest {

    private static final String CALLER_PASSWORD = "CallerPass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformAdminRepository platformAdminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String callerAccessToken;

    @BeforeEach
    void seedAuthenticatedCaller() throws Exception {
        // El actor real de estos endpoints es SUPER_ADMIN (PlatformAdmin,
        // sin company_id, sin filtro de tenant activo). Autenticar con un
        // User de empresa común activaría el tenantFilter scoped a esa
        // empresa y ocultaría usuarios de otras empresas en las consultas
        // (ej. el chequeo de email duplicado), dando falsos negativos.
        PlatformAdmin caller = new PlatformAdmin();
        caller.setEmail("caller@platform.com");
        caller.setPasswordHash(passwordEncoder.encode(CALLER_PASSWORD));
        caller.setEstado(EstadoPlatformAdmin.ACTIVO);
        platformAdminRepository.save(caller);
        entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", "caller@platform.com",
                "password", CALLER_PASSWORD));
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        callerAccessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();
    }

    private String createCompanyRequestBody(String adminEmail) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "nombre", "Heladería Don José",
                "razonSocial", "Don José SRL",
                "pais", "AR",
                "moneda", "ARS",
                "zonaHoraria", "America/Argentina/Buenos_Aires",
                "adminEmail", adminEmail));
    }

    @Test
    void createsCompanyWithInitialAdminAndReturnsTemporaryPassword() throws Exception {
        String body = createCompanyRequestBody("admin@donjose.com");

        String responseJson = mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", "Bearer " + callerAccessToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company.nombre").value("Heladería Don José"))
                .andExpect(jsonPath("$.company.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.adminEmail").value("admin@donjose.com"))
                .andExpect(jsonPath("$.adminTemporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(responseJson);
        String temporaryPassword = json.get("adminTemporaryPassword").asText();

        // el admin recién creado puede loguearse con la temporal, y debe
        // cambiar la contraseña en su primer login (RF-01)
        String adminLoginBody = objectMapper.writeValueAsString(Map.of(
                "email", "admin@donjose.com",
                "password", temporaryPassword));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(adminLoginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.rol").value("ADMIN"))
                .andExpect(jsonPath("$.user.debeCambiarPassword").value(true));
    }

    @Test
    void createWithDuplicateAdminEmailReturns409() throws Exception {
        String body = createCompanyRequestBody("dup@donjose.com");
        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", "Bearer " + callerAccessToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", "Bearer " + callerAccessToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void getUnknownCompanyReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + callerAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void listGetUpdateAndUpdateStatusLifecycle() throws Exception {
        String createBody = createCompanyRequestBody("admin@lifecycle.com");
        String createResponseJson = mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", "Bearer " + callerAccessToken)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String companyId = objectMapper.readTree(createResponseJson).get("company").get("id").asText();

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", "Bearer " + callerAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/companies/" + companyId)
                        .header("Authorization", "Bearer " + callerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Heladería Don José"));

        String updateBody = objectMapper.writeValueAsString(Map.of("nombre", "Heladería Don José 2"));
        mockMvc.perform(patch("/api/v1/companies/" + companyId)
                        .header("Authorization", "Bearer " + callerAccessToken)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Heladería Don José 2"))
                .andExpect(jsonPath("$.pais").value("AR"));

        String statusBody = objectMapper.writeValueAsString(Map.of("estado", "SUSPENDIDA"));
        mockMvc.perform(patch("/api/v1/companies/" + companyId + "/status")
                        .header("Authorization", "Bearer " + callerAccessToken)
                        .contentType("application/json")
                        .content(statusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SUSPENDIDA"));
    }

    @Test
    void createWithoutAuthenticationReturns401() throws Exception {
        String body = createCompanyRequestBody("noauth@donjose.com");
        mockMvc.perform(post("/api/v1/companies")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
