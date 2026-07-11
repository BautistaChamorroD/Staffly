package com.staffly.backend.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkpoint de verificación de aislamiento multi-tenant para toda la Fase 1
 * (BE-1.9). Consolida en un solo lugar la cobertura de los endpoints que
 * ningún ControllerTest individual cubre todavía contra un recurso de otra
 * empresa: los PATCH .../status de Branch/User/Employee, GET .../history de
 * Employee, y un intento de inyección de companyId en el body de un POST.
 *
 * Company queda fuera: no es una entidad tenant-scoped (no extiende
 * TenantAwareEntity), su control de acceso es por rol (SUPER_ADMIN), no por
 * aislamiento entre tenants (ya cubierto en BE-1.7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantIsolationTest {

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

    private String branchAId;
    private String userAId;
    private String employeeAId;
    private String companyBToken;

    @BeforeEach
    void seedTwoFullCompanies() throws Exception {
        // Company A: dueña de los recursos que Company B va a intentar tocar.
        Branch branchA = createBranch(createCompany("Empresa A"), "Sucursal A");
        branchAId = branchA.getId().toString();

        Employee employeeA = createEmployee(branchA);
        employeeAId = employeeA.getId().toString();

        User adminA = createUser(branchA.getCompanyId(), "admin-a@empresa-a.com", RolUsuario.ADMIN, null);
        userAId = adminA.getId().toString();

        // Company B: la que intenta el acceso indebido.
        Branch branchB = createBranch(createCompany("Empresa B"), "Sucursal B");
        User adminB = createUser(branchB.getCompanyId(), "admin-b@empresa-b.com", RolUsuario.ADMIN, null);
        companyBToken = login(adminB.getEmail());
    }

    private java.util.UUID createCompany(String nombre) {
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

    private Branch createBranch(java.util.UUID companyId, String nombre) {
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

    private Employee createEmployee(Branch branch) {
        Employee employee = new Employee();
        employee.setCompanyId(branch.getCompanyId());
        employee.setNombre("Empleado");
        employee.setApellido("De Empresa A");
        employee.setDocumento("30999888");
        employee.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        employee.setFechaIngreso(LocalDate.of(2024, 1, 1));
        employee.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        employee.setCategoria("Vendedor");
        employee.setSueldoBase(new BigDecimal("500000"));
        employee.setEstadoLaboral(EstadoLaboral.ACTIVO);
        employee.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        employee.getBranches().add(branch);
        entityManager.persist(employee);
        entityManager.flush();
        return employee;
    }

    private User createUser(java.util.UUID companyId, String email, RolUsuario rol, Employee employee) {
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
        userRepository.save(user);
        entityManager.flush();
        return user;
    }

    private String login(String email) throws Exception {
        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }

    @Test
    void cannotChangeStatusOfAnotherCompanysBranch() throws Exception {
        mockMvc.perform(patch("/api/v1/branches/" + branchAId + "/status")
                        .header("Authorization", "Bearer " + companyBToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estado", "INACTIVA"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cannotChangeStatusOfAnotherCompanysUser() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + userAId + "/status")
                        .header("Authorization", "Bearer " + companyBToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estado", "INACTIVO"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cannotChangeStatusOfAnotherCompanysEmployee() throws Exception {
        mockMvc.perform(patch("/api/v1/employees/" + employeeAId + "/status")
                        .header("Authorization", "Bearer " + companyBToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estadoLaboral", "BAJA"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cannotViewHistoryOfAnotherCompanysEmployee() throws Exception {
        mockMvc.perform(get("/api/v1/employees/" + employeeAId + "/history")
                        .header("Authorization", "Bearer " + companyBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void companyIdInRequestBodyIsIgnored_alwaysTakenFromToken() throws Exception {
        // Autenticado como Company A, con un companyId ajeno (el de Company B)
        // colado en el body. El DTO de creación no tiene ese campo, Jackson lo
        // descarta: la sucursal creada debe quedar igual con el company_id de
        // Company A, nunca el inyectado.
        String adminAToken = login("admin-a@empresa-a.com");

        Map<String, Object> body = new HashMap<>();
        body.put("nombre", "Sucursal Con Intento De Inyeccion");
        body.put("direccion", "Alguna Direccion");
        body.put("zonaHoraria", "America/Argentina/Buenos_Aires");
        body.put("companyId", java.util.UUID.randomUUID().toString());

        String createResponse = mockMvc.perform(post("/api/v1/branches")
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String newBranchId = objectMapper.readTree(createResponse).get("id").asText();

        // si el companyId inyectado hubiera sido respetado, esta consulta
        // (con el token real de Company A) no encontraría la sucursal
        mockMvc.perform(get("/api/v1/branches/" + newBranchId)
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());
    }

    @Test
    void listEndpointsNeverLeakOtherCompanysData() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .header("Authorization", "Bearer " + companyBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + branchAId + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", "Bearer " + companyBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + employeeAId + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + companyBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + userAId + "')]").isEmpty());
    }
}
