package com.staffly.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.platform.EstadoPlatformAdmin;
import com.staffly.backend.platform.PlatformAdmin;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

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
    private RevokedTokenRepository revokedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Company company;
    private User user;

    @BeforeEach
    void seedUser() {
        company = new Company();
        company.setNombre("Heladería Test");
        company.setRazonSocial("Heladería Test SRL");
        company.setPais("AR");
        company.setMoneda("ARS");
        company.setZonaHoraria("America/Argentina/Buenos_Aires");
        company.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(company);

        user = new User();
        user.setCompanyId(company.getId());
        user.setEmail("admin@heladeria-test.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(RolUsuario.ADMIN);
        user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(true);
        userRepository.save(user);

        entityManager.flush();
    }

    private String loginAndGetRefreshToken() throws Exception {
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", "admin@heladeria-test.com",
                "password", PASSWORD));
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("refreshToken").asText();
    }

    @Test
    void fullAuthLifecycle() throws Exception {
        // login
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", "admin@heladeria-test.com",
                "password", PASSWORD));

        String loginResponseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.debeCambiarPassword").value(true))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> loginResponse = objectMapper.readValue(loginResponseJson, Map.class);
        String refreshToken1 = (String) loginResponse.get("refreshToken");

        // refresh (rotation: refreshToken1 gets revoked, a new one is issued)
        String refreshBody1 = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken1));
        String refreshResponseJson = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> refreshResponse = objectMapper.readValue(refreshResponseJson, Map.class);
        String accessToken2 = (String) refreshResponse.get("accessToken");
        String refreshToken2 = (String) refreshResponse.get("refreshToken");

        // reusing the old (now revoked) refresh token must fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody1))
                .andExpect(status().isUnauthorized());

        // logout revokes refreshToken2
        String logoutBody = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken2));
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType("application/json")
                        .content(logoutBody))
                .andExpect(status().isNoContent());

        // refreshToken2 is now revoked too
        String refreshBody2 = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken2));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody2))
                .andExpect(status().isUnauthorized());

        // change-password with the still-valid access token
        String changePasswordBody = objectMapper.writeValueAsString(Map.of(
                "currentPassword", PASSWORD,
                "newPassword", "NewPassword456"));
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType("application/json")
                        .content(changePasswordBody))
                .andExpect(status().isNoContent());

        // login again with the new password confirms debeCambiarPassword was cleared
        String newLoginBody = objectMapper.writeValueAsString(Map.of(
                "email", "admin@heladeria-test.com",
                "password", "NewPassword456"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(newLoginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.debeCambiarPassword").value(false));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "admin@heladeria-test.com",
                "password", "wrong-password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutTokenReturns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "irrelevant"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginOfUserFromSuspendedCompanyReturns401() throws Exception {
        company.setEstado(EstadoEmpresa.SUSPENDIDA);
        entityManager.flush();

        String body = objectMapper.writeValueAsString(Map.of(
                "email", "admin@heladeria-test.com",
                "password", PASSWORD));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginOfInactivePlatformAdminReturns401() throws Exception {
        PlatformAdmin platformAdmin = new PlatformAdmin();
        platformAdmin.setEmail("superadmin@staffly.com");
        platformAdmin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        platformAdmin.setEstado(EstadoPlatformAdmin.INACTIVO);
        entityManager.persist(platformAdmin);
        entityManager.flush();

        String body = objectMapper.writeValueAsString(Map.of(
                "email", "superadmin@staffly.com",
                "password", PASSWORD));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshAfterUserDeactivatedReturns401() throws Exception {
        String refreshToken = loginAndGetRefreshToken();

        user.setEstado(EstadoUsuario.INACTIVO);
        entityManager.flush();

        String refreshBody = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshAfterCompanySuspendedReturns401() throws Exception {
        String refreshToken = loginAndGetRefreshToken();

        company.setEstado(EstadoEmpresa.SUSPENDIDA);
        entityManager.flush();

        String refreshBody = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshPurgesExpiredRevokedTokens() throws Exception {
        UUID expiredJti = UUID.randomUUID();
        revokedTokenRepository.save(new RevokedToken(expiredJti, Instant.now().minusSeconds(60)));
        entityManager.flush();

        String refreshToken = loginAndGetRefreshToken();
        String refreshBody = objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk());

        assertThat(revokedTokenRepository.existsById(expiredJti)).isFalse();
    }
}
