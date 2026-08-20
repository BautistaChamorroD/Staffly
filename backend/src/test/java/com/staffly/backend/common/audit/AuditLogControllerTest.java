package com.staffly.backend.common.audit;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/audit-log";

    @Autowired private MockMvc         mockMvc;
    @Autowired private ObjectMapper    objectMapper;
    @Autowired private EntityManager   entityManager;
    @Autowired private UserRepository  userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID   companyAId;
    private UUID   usuarioId;
    private String adminAToken;
    private String rrhhAToken;

    @BeforeEach
    void seed() throws Exception {
        companyAId  = createCompany("Empresa A");
        adminAToken = createUserAndLogin(companyAId, "admin@a.com", RolUsuario.ADMIN);
        rrhhAToken  = createUserAndLogin(companyAId, "rrhh@a.com", RolUsuario.RRHH);
        usuarioId   = userRepository.findByEmail("admin@a.com").orElseThrow().getId();
    }

    @Test
    void adminPuedeConsultar() throws Exception {
        createEntry(companyAId, "EMPLOYEE", UUID.randomUUID(), usuarioId, "categoria", "A", "B", Instant.now());

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rrhhNoPuedeConsultar() throws Exception {
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + rrhhAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtroPorEntidad() throws Exception {
        createEntry(companyAId, "EMPLOYEE", UUID.randomUUID(), usuarioId, "categoria", "A", "B", Instant.now());
        createEntry(companyAId, "PAYSLIP", UUID.randomUUID(), usuarioId, "estado", "PAGADO", "ANULADO", Instant.now());

        mockMvc.perform(get(BASE_URL + "?entidad=PAYSLIP").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityType").value("PAYSLIP"));
    }

    @Test
    void filtroPorEntidadId() throws Exception {
        UUID targetId = UUID.randomUUID();
        createEntry(companyAId, "ADVANCE", targetId, usuarioId, "estado", null, "PENDIENTE", Instant.now());
        createEntry(companyAId, "ADVANCE", UUID.randomUUID(), usuarioId, "estado", null, "PENDIENTE", Instant.now());

        mockMvc.perform(get(BASE_URL + "?entidadId=" + targetId).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityId").value(targetId.toString()));
    }

    @Test
    void filtroPorUserId() throws Exception {
        UUID otroUsuario = UUID.randomUUID();
        createEntry(companyAId, "ADVANCE", UUID.randomUUID(), usuarioId, "estado", null, "PENDIENTE", Instant.now());
        createEntry(companyAId, "ADVANCE", UUID.randomUUID(), otroUsuario, "estado", null, "PENDIENTE", Instant.now());

        mockMvc.perform(get(BASE_URL + "?userId=" + usuarioId).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].usuarioId").value(usuarioId.toString()));
    }

    @Test
    void filtroPorRangoDeFechas() throws Exception {
        createEntry(companyAId, "ADVANCE", UUID.randomUUID(), usuarioId, "estado", null, "PENDIENTE",
                Instant.parse("2026-01-01T00:00:00Z"));
        createEntry(companyAId, "ADVANCE", UUID.randomUUID(), usuarioId, "estado", null, "PENDIENTE",
                Instant.parse("2026-07-15T00:00:00Z"));

        mockMvc.perform(get(BASE_URL + "?desde=2026-07-01&hasta=2026-07-31")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void tenantIsolation() throws Exception {
        UUID companyBId = createCompany("Empresa B");
        createEntry(companyBId, "ADVANCE", UUID.randomUUID(), UUID.randomUUID(), "estado", null, "PENDIENTE", Instant.now());

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID createCompany(String nombre) {
        Company c = new Company();
        c.setNombre(nombre);
        c.setRazonSocial(nombre + " SRL");
        c.setPais("AR");
        c.setMoneda("ARS");
        c.setZonaHoraria("America/Argentina/Buenos_Aires");
        c.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(c);
        entityManager.flush();
        return c.getId();
    }

    private void createEntry(UUID companyId, String entityType, UUID entityId, UUID usuarioId,
                             String campo, String valorAnterior, String valorNuevo, Instant fecha) {
        AuditLog a = new AuditLog();
        a.setCompanyId(companyId);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setUsuarioId(usuarioId);
        a.setCampo(campo);
        a.setValorAnterior(valorAnterior);
        a.setValorNuevo(valorNuevo);
        entityManager.persist(a);
        entityManager.flush();
        // fecha tiene un valor por defecto al construir la entidad — lo pisamos después del persist para tests de rango
        entityManager.createNativeQuery("UPDATE audit_log SET fecha = ?1 WHERE id = ?2")
                .setParameter(1, fecha.truncatedTo(ChronoUnit.MILLIS))
                .setParameter(2, a.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol) throws Exception {
        User u = new User();
        u.setCompanyId(companyId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRol(rol);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setDebeCambiarPassword(false);
        userRepository.save(u);
        entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResp).get("accessToken").asText();
    }
}
