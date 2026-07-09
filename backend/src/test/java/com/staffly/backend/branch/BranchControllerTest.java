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

    private UUID companyAId;
    private String companyAToken;
    private String companyBToken;

    @BeforeEach
    void seedTwoCompaniesWithAdmins() throws Exception {
        companyAId = createCompany("Empresa A");
        companyAToken = createUserAndLogin(companyAId, "admin-a@empresa-a.com", RolUsuario.ADMIN, null);

        UUID companyBId = createCompany("Empresa B");
        companyBToken = createUserAndLogin(companyBId, "admin-b@empresa-b.com", RolUsuario.ADMIN, null);
    }

    private UUID createCompany(String companyName) {
        Company company = new Company();
        company.setNombre(companyName);
        company.setRazonSocial(companyName + " SRL");
        company.setPais("AR");
        company.setMoneda("ARS");
        company.setZonaHoraria("America/Argentina/Buenos_Aires");
        company.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(company);
        return company.getId();
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol, Branch assignedBranch) throws Exception {
        User user = new User();
        user.setCompanyId(companyId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(rol);
        user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(false);
        if (assignedBranch != null) {
            user.getBranches().add(assignedBranch);
        }
        userRepository.save(user);
        entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }

    private Branch createBranchEntity(UUID companyId, String nombre) {
        Branch branch = new Branch();
        branch.setCompanyId(companyId);
        branch.setNombre(nombre);
        branch.setDireccion("Direccion");
        branch.setZonaHoraria("America/Argentina/Buenos_Aires");
        branch.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(branch);
        entityManager.flush();
        return branch;
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

    @Test
    void rrhhCannotCreateBranchReturns403() throws Exception {
        String rrhhToken = createUserAndLogin(companyAId, "rrhh-a@empresa-a.com", RolUsuario.RRHH, null);

        mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + rrhhToken)
                        .contentType("application/json")
                        .content(createBranchRequestBody("Sucursal RRHH")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void supervisorOnlySeesAssignedBranches() throws Exception {
        Branch assignedBranch = createBranchEntity(companyAId, "Sucursal Asignada");
        Branch otherBranch = createBranchEntity(companyAId, "Sucursal No Asignada");
        String supervisorToken = createUserAndLogin(companyAId, "supervisor-a@empresa-a.com", RolUsuario.SUPERVISOR, assignedBranch);

        mockMvc.perform(get("/api/v1/branches")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Sucursal Asignada"));

        mockMvc.perform(get("/api/v1/branches/" + assignedBranch.getId())
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk());

        // misma empresa, pero no es su sucursal asignada -> 404, no 403
        mockMvc.perform(get("/api/v1/branches/" + otherBranch.getId())
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
