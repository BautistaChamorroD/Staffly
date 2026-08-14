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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayrollConfigControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/payroll-config";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private String adminAToken;
    private String rrhhToken;

    @BeforeEach
    void setUp() throws Exception {
        companyAId = createCompany("Empresa A");
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN);
        rrhhToken   = createUserAndLogin(companyAId, "rrhh@a.com", RolUsuario.RRHH);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void getCreatesDefaultConfigIfNotExists() throws Exception {
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralHorasExtra").value(8))
                .andExpect(jsonPath("$.multiplicadorHoraExtra").value(1.5))
                .andExpect(jsonPath("$.multiplicadorFeriado").value(2.0))
                .andExpect(jsonPath("$.periodicidad").value("MENSUAL"))
                .andExpect(jsonPath("$.conceptosDescuento").isArray());
    }

    @Test
    void getIsIdempotent() throws Exception {
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        // solo una fila en la tabla
        Long count = (Long) entityManager
                .createQuery("SELECT COUNT(c) FROM PayrollConfig c WHERE c.companyId = :id")
                .setParameter("id", companyAId)
                .getSingleResult();
        org.junit.jupiter.api.Assertions.assertEquals(1L, count);
    }

    @Test
    void updateConfig() throws Exception {
        Map<String, Object> body = Map.of(
                "umbralHorasExtra", 9,
                "multiplicadorHoraExtra", 1.75,
                "multiplicadorFeriado", 2.5,
                "periodicidad", "QUINCENAL",
                "conceptosDescuento", List.of(
                        Map.of("nombre", "Obra Social", "tipo", "PORCENTAJE", "valor", 3),
                        Map.of("nombre", "Sindicato",   "tipo", "MONTO_FIJO", "valor", 500)
                )
        );

        mockMvc.perform(put(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralHorasExtra").value(9))
                .andExpect(jsonPath("$.periodicidad").value("QUINCENAL"))
                .andExpect(jsonPath("$.conceptosDescuento.length()").value(2))
                .andExpect(jsonPath("$.conceptosDescuento[0].nombre").value("Obra Social"))
                .andExpect(jsonPath("$.conceptosDescuento[1].tipo").value("MONTO_FIJO"));
    }

    @Test
    void updateIsUpsert() throws Exception {
        Map<String, Object> body = Map.of(
                "umbralHorasExtra", 8, "multiplicadorHoraExtra", 1.5,
                "multiplicadorFeriado", 2.0, "periodicidad", "MENSUAL",
                "conceptosDescuento", List.of()
        );

        mockMvc.perform(put(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Long count = (Long) entityManager
                .createQuery("SELECT COUNT(c) FROM PayrollConfig c WHERE c.companyId = :id")
                .setParameter("id", companyAId)
                .getSingleResult();
        org.junit.jupiter.api.Assertions.assertEquals(1L, count);
    }

    @Test
    void rrhhCannotAccessConfig() throws Exception {
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + rrhhToken))
                .andExpect(status().isForbidden());

        Map<String, Object> body = Map.of(
                "umbralHorasExtra", 8, "multiplicadorHoraExtra", 1.5,
                "multiplicadorFeriado", 2.0, "periodicidad", "MENSUAL",
                "conceptosDescuento", List.of()
        );
        mockMvc.perform(put(BASE_URL)
                        .header("Authorization", "Bearer " + rrhhToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTipoConceptoReturns400() throws Exception {
        Map<String, Object> body = Map.of(
                "umbralHorasExtra", 8, "multiplicadorHoraExtra", 1.5,
                "multiplicadorFeriado", 2.0, "periodicidad", "MENSUAL",
                "conceptosDescuento", List.of(
                        Map.of("nombre", "X", "tipo", "INVALIDO", "valor", 10)
                )
        );
        mockMvc.perform(put(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantIsolation() throws Exception {
        UUID companyBId = createCompany("Empresa B");
        String adminBToken = createUserAndLogin(companyBId, "admin@b.com", RolUsuario.ADMIN);

        // Empresa B configura con valor distinto
        Map<String, Object> bodyB = Map.of(
                "umbralHorasExtra", 40, "multiplicadorHoraExtra", 2.0,
                "multiplicadorFeriado", 3.0, "periodicidad", "QUINCENAL",
                "conceptosDescuento", List.of()
        );
        mockMvc.perform(put(BASE_URL)
                        .header("Authorization", "Bearer " + adminBToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(bodyB)))
                .andExpect(status().isOk());

        // Empresa A ve su propia config (por defecto)
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralHorasExtra").value(8))
                .andExpect(jsonPath("$.periodicidad").value("MENSUAL"));
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
}
