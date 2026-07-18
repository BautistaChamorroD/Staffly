package com.staffly.backend.employee;

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

import java.math.BigDecimal;
import java.time.LocalDate;
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
class EmployeeControllerTest {

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
        companyAToken = createUserAndLogin(companyAId, "admin-a@empresa-a.com", RolUsuario.ADMIN, null);

        companyBId = createCompany("Empresa B");
        companyBToken = createUserAndLogin(companyBId, "admin-b@empresa-b.com", RolUsuario.ADMIN, null);
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

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol, Employee employee) throws Exception {
        return createUserAndLogin(companyId, email, rol, employee, null);
    }

    private String createUserAndLogin(
            UUID companyId, String email, RolUsuario rol, Employee employee, Branch assignedBranch) throws Exception {
        User user = new User();
        user.setCompanyId(companyId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(rol);
        user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(false);
        if (employee != null) {
            user.setEmployee(employee);
        }
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

    private Map<String, Object> baseEmployeeFields(UUID branchId) {
        return Map.of(
                "nombre", "Juan",
                "apellido", "Pérez",
                "documento", "30111222",
                "fechaNacimiento", "1990-01-15",
                "fechaIngreso", "2024-03-01",
                "tipoContrato", "JORNADA_COMPLETA",
                "categoria", "Vendedor",
                "sueldoBase", new BigDecimal("500000"),
                "branchIds", List.of(branchId.toString()));
    }

    @Test
    void createsEmployeeScopedToCallerCompany() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.estadoLaboral").value("ACTIVO"))
                .andExpect(jsonPath("$.estadoLiquidacion").value("AL_DIA"))
                .andExpect(jsonPath("$.branchIds.length()").value(1));
    }

    @Test
    void createWithoutBranchIdsReturns400() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(baseEmployeeFields(UUID.randomUUID()));
        body.put("branchIds", List.of());

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithNegativeSueldoReturns400() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        Map<String, Object> body = new java.util.HashMap<>(baseEmployeeFields(branchId));
        body.put("sueldoBase", new BigDecimal("-1000"));

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithDuplicateDocumentoInSameCompanyReturns409() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchId))))
                .andExpect(status().isCreated());

        Map<String, Object> duplicado = new java.util.HashMap<>(baseEmployeeFields(branchId));
        duplicado.put("nombre", "Otro");
        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void createWithSameDocumentoInAnotherCompanyIsAllowed() throws Exception {
        UUID branchA = createBranch(companyAId, "Sucursal A");
        UUID branchB = createBranch(companyBId, "Sucursal B");

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchA))))
                .andExpect(status().isCreated());

        // mismo documento en OTRA empresa: válido, la unicidad es por tenant
        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyBToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchB))))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithFechaEgresoBeforeFechaIngresoReturns400() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        Map<String, Object> body = new java.util.HashMap<>(baseEmployeeFields(branchId));
        body.put("fechaEgreso", "2024-02-01"); // fechaIngreso es 2024-03-01

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateWithFechaEgresoBeforeStoredFechaIngresoReturns400() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        String createResponse = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String employeeId = objectMapper.readTree(createResponse).get("id").asText();

        // la fechaIngreso guardada es 2024-03-01: un egreso anterior es inválido
        // aunque el PATCH no mande fechaIngreso
        mockMvc.perform(patch("/api/v1/employees/" + employeeId)
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("fechaEgreso", "2024-02-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createWithBranchFromAnotherCompanyReturns404() throws Exception {
        UUID branchFromCompanyB = createBranch(companyBId, "Sucursal B");

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchFromCompanyB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantIsolation_companyCannotSeeOrEditOtherCompanyEmployee() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        String createResponse = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String employeeId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/employees/" + employeeId)
                        .header("Authorization", "Bearer " + companyBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/employees/" + employeeId)
                        .header("Authorization", "Bearer " + companyBToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("categoria", "Hackeado"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatusToBajaDoesNotBlockOnPendingLiquidacion() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        String createResponse = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String employeeId = objectMapper.readTree(createResponse).get("id").asText();

        // estadoLiquidacion sigue AL_DIA (no editable por API), pero RF-07b dice
        // que la baja no se bloquea aunque estuviera PENDIENTE de todos modos.
        mockMvc.perform(patch("/api/v1/employees/" + employeeId + "/status")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estadoLaboral", "BAJA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoLaboral").value("BAJA"));
    }

    @Test
    void updateReassignsBranches() throws Exception {
        UUID branchOriginal = createBranch(companyAId, "Sucursal Original");
        UUID branchNueva = createBranch(companyAId, "Sucursal Nueva");
        String createResponse = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchOriginal))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String employeeId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(patch("/api/v1/employees/" + employeeId)
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("branchIds", List.of(branchNueva.toString())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchIds.length()").value(1))
                .andExpect(jsonPath("$.branchIds[0]").value(branchNueva.toString()));
    }

    @Test
    void listFiltersByEstadoLaboralBranchAndSearch() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchId))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/employees").param("estadoLaboral", "ACTIVO")
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/employees").param("branchId", branchId.toString())
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/employees").param("search", "perez")
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0)); // "Pérez" con acento, "perez" sin acento no matchea (contains simple)

        mockMvc.perform(get("/api/v1/employees").param("search", "Pérez")
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void historyReturnsEmptyList() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        String createResponse = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String employeeId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/employees/" + employeeId + "/history")
                        .header("Authorization", "Bearer " + companyAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void meReturnsLinkedEmployee() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        Employee employee = new Employee();
        employee.setCompanyId(companyAId);
        employee.setNombre("Empleado");
        employee.setApellido("Vinculado");
        employee.setDocumento("40111222");
        employee.setFechaNacimiento(LocalDate.of(1995, 5, 20));
        employee.setFechaIngreso(LocalDate.of(2024, 1, 1));
        employee.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        employee.setCategoria("Cajero");
        employee.setSueldoBase(new BigDecimal("400000"));
        employee.setEstadoLaboral(EstadoLaboral.ACTIVO);
        employee.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        entityManager.persist(employee);
        entityManager.flush();

        String employeeUserToken = createUserAndLogin(companyAId, "empleado@empresa-a.com", RolUsuario.EMPLOYEE, employee);

        mockMvc.perform(get("/api/v1/employees/me")
                        .header("Authorization", "Bearer " + employeeUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Empleado"))
                .andExpect(jsonPath("$.documento").value("40111222"));
    }

    @Test
    void createWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeRoleCannotAccessListOrGetById() throws Exception {
        UUID branchId = createBranch(companyAId, "Sucursal Centro");
        String createResponse = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(branchId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String employeeId = objectMapper.readTree(createResponse).get("id").asText();

        String employeeToken = createUserAndLogin(companyAId, "empleado-raso@empresa-a.com", RolUsuario.EMPLOYEE, null);

        mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // ni siquiera puede pedir su propio registro por /{id}, solo por /me (RF-29)
        mockMvc.perform(get("/api/v1/employees/" + employeeId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorOnlySeesEmployeesFromAssignedBranches() throws Exception {
        Branch assignedBranch = createBranchEntity(companyAId, "Sucursal Asignada");
        UUID otherBranchId = createBranch(companyAId, "Sucursal No Asignada");

        String createOwnResponse = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(assignedBranch.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ownEmployeeId = objectMapper.readTree(createOwnResponse).get("id").asText();

        // documento distinto: ahora es único por empresa
        Map<String, Object> otherEmployeeFields = new java.util.HashMap<>(baseEmployeeFields(otherBranchId));
        otherEmployeeFields.put("documento", "30999888");
        String createOtherResponse = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(otherEmployeeFields)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String otherEmployeeId = objectMapper.readTree(createOtherResponse).get("id").asText();

        String supervisorToken = createUserAndLogin(
                companyAId, "supervisor-a@empresa-a.com", RolUsuario.SUPERVISOR, null, assignedBranch);

        mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownEmployeeId));

        mockMvc.perform(get("/api/v1/employees/" + ownEmployeeId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk());

        // mismo tenant, pero de una sucursal que no le fue asignada -> 404
        mockMvc.perform(get("/api/v1/employees/" + otherEmployeeId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void supervisorCannotCreateOrEditEmployees() throws Exception {
        Branch assignedBranch = createBranchEntity(companyAId, "Sucursal Asignada");
        String supervisorToken = createUserAndLogin(
                companyAId, "supervisor-b@empresa-a.com", RolUsuario.SUPERVISOR, null, assignedBranch);

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(baseEmployeeFields(assignedBranch.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
