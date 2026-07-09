package com.staffly.backend.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

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

    private UUID companyAId;
    private UUID companyBId;
    private String companyAToken;
    private String companyBToken;

    @BeforeEach
    void seedTwoCompaniesWithAdmins() throws Exception {
        companyAId = createCompany("Empresa A");
        companyAToken = createAdminAndLogin(companyAId, "admin-a@empresa-a.com");

        companyBId = createCompany("Empresa B");
        companyBToken = createAdminAndLogin(companyBId, "admin-b@empresa-b.com");
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
        return company.getId();
    }

    private String createAdminAndLogin(UUID companyId, String email) throws Exception {
        User admin = new User();
        admin.setCompanyId(companyId);
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

    private UUID createBranch(UUID companyId, String nombre) {
        Branch branch = new Branch();
        branch.setCompanyId(companyId);
        branch.setNombre(nombre);
        branch.setDireccion("Direccion");
        branch.setZonaHoraria("America/Argentina/Buenos_Aires");
        branch.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(branch);
        entityManager.flush();
        return branch.getId();
    }

    @Test
    void createsRrhhUserAndReturnsTemporaryPassword() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "rrhh@empresa-a.com",
                "rol", "RRHH"));

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("rrhh@empresa-a.com"))
                .andExpect(jsonPath("$.user.rol").value("RRHH"))
                .andExpect(jsonPath("$.user.debeCambiarPassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty());
    }

    @Test
    void createsSupervisorWithValidBranchIds() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");

        String body = objectMapper.writeValueAsString(Map.of(
                "email", "supervisor@empresa-a.com",
                "rol", "SUPERVISOR",
                "branchIds", List.of(branchId.toString())));

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.rol").value("SUPERVISOR"))
                .andExpect(jsonPath("$.user.branchIds.length()").value(1));
    }

    @Test
    void createsSupervisorWithBranchFromAnotherCompanyReturns404() throws Exception {
        UUID branchFromCompanyB = createBranch(companyBId, "Sucursal de Empresa B");

        String body = objectMapper.writeValueAsString(Map.of(
                "email", "supervisor2@empresa-a.com",
                "rol", "SUPERVISOR",
                "branchIds", List.of(branchFromCompanyB.toString())));

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantIsolation_companyCannotSeeOrEditOtherCompanyUser() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "email", "privado@empresa-a.com",
                "rol", "RRHH"));
        String createResponse = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userAId = objectMapper.readTree(createResponse).get("user").get("id").asText();

        mockMvc.perform(get("/api/v1/users/" + userAId)
                        .header("Authorization", "Bearer " + companyBToken))
                .andExpect(status().isNotFound());

        String updateBody = objectMapper.writeValueAsString(Map.of("rol", "SUPERVISOR"));
        mockMvc.perform(patch("/api/v1/users/" + userAId)
                        .header("Authorization", "Bearer " + companyBToken)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void changingRoleAwayFromSupervisorClearsBranchIds() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal X");
        String createBody = objectMapper.writeValueAsString(Map.of(
                "email", "supervisor3@empresa-a.com",
                "rol", "SUPERVISOR",
                "branchIds", List.of(branchId.toString())));
        String createResponse = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userId = objectMapper.readTree(createResponse).get("user").get("id").asText();

        String updateBody = objectMapper.writeValueAsString(Map.of("rol", "RRHH"));
        mockMvc.perform(patch("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("RRHH"))
                .andExpect(jsonPath("$.branchIds.length()").value(0));
    }

    @Test
    void getMeReturnsAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin-a@empresa-a.com"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void listFiltersByRolAndEstado() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", "filtro1@empresa-a.com", "rol", "RRHH"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/users").param("rol", "RRHH")
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/users").param("rol", "SUPERVISOR")
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateStatusLifecycle() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", "status@empresa-a.com", "rol", "RRHH"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userId = objectMapper.readTree(createResponse).get("user").get("id").asText();

        mockMvc.perform(patch("/api/v1/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estado", "INACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    void createWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", "sinauth@empresa-a.com", "rol", "RRHH"))))
                .andExpect(status().isUnauthorized());
    }
}
