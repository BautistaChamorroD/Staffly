package com.staffly.backend.availability;

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
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AvailabilityControllerTest {

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
    private String adminAToken;
    private Branch branchA1;
    private Branch branchA2;
    private Employee employeeA1;
    private Employee employeeA2;
    private int documentoSeq = 0;

    @BeforeEach
    void seedTwoCompanies() throws Exception {
        companyAId = createCompany("Empresa A");
        branchA1 = createBranch(companyAId, "Sucursal A1");
        branchA2 = createBranch(companyAId, "Sucursal A2");
        employeeA1 = createEmployee(companyAId, branchA1);
        employeeA2 = createEmployee(companyAId, branchA2);
        adminAToken = createUserAndLogin(companyAId, "admin-a@empresa-a.com", RolUsuario.ADMIN, null, null);

        companyBId = createCompany("Empresa B");
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

    private Branch createBranch(UUID companyId, String nombre) {
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

    private Employee createEmployee(UUID companyId, Branch branch) {
        Employee employee = new Employee();
        employee.setCompanyId(companyId);
        employee.setNombre("Empleado");
        employee.setApellido("Numero" + documentoSeq);
        employee.setDocumento("3000000" + documentoSeq++);
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

    private String availabilityUrl(UUID employeeId) {
        return "/api/v1/employees/" + employeeId + "/availability";
    }

    private String createFranja(String token, UUID employeeId, String dia, String inicio, String fin) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "diaSemana", dia, "horaInicio", inicio, "horaFin", fin));
        String response = mockMvc.perform(post(availabilityUrl(employeeId))
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void employeeManagesOwnAvailabilityLifecycle() throws Exception {
        String employeeToken = createUserAndLogin(
                companyAId, "empleado1@empresa-a.com", RolUsuario.EMPLOYEE, employeeA1, null);

        // crea desordenado: el GET debe devolver LUNES antes que MIERCOLES
        createFranja(employeeToken, employeeA1.getId(), "MIERCOLES", "14:00", "18:00");
        String franjaLunesId = createFranja(employeeToken, employeeA1.getId(), "LUNES", "09:00", "13:00");

        mockMvc.perform(get(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].diaSemana").value("LUNES"))
                .andExpect(jsonPath("$[1].diaSemana").value("MIERCOLES"));

        // PATCH parcial: solo horaFin
        mockMvc.perform(patch(availabilityUrl(employeeA1.getId()) + "/" + franjaLunesId)
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("horaFin", "17:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaSemana").value("LUNES"))
                .andExpect(jsonPath("$.horaFin").value("17:00:00"));

        mockMvc.perform(delete(availabilityUrl(employeeA1.getId()) + "/" + franjaLunesId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void adminAndRrhhManageAnyEmployeesAvailability() throws Exception {
        String rrhhToken = createUserAndLogin(companyAId, "rrhh@empresa-a.com", RolUsuario.RRHH, null, null);

        createFranja(adminAToken, employeeA1.getId(), "LUNES", "09:00", "13:00");
        createFranja(rrhhToken, employeeA2.getId(), "MARTES", "10:00", "14:00");

        mockMvc.perform(get(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + rrhhToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void employeeCannotTouchAnotherEmployeesAvailability() throws Exception {
        String employeeToken = createUserAndLogin(
                companyAId, "empleado1@empresa-a.com", RolUsuario.EMPLOYEE, employeeA1, null);

        mockMvc.perform(get(availabilityUrl(employeeA2.getId()))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String body = objectMapper.writeValueAsString(Map.of(
                "diaSemana", "LUNES", "horaInicio", "09:00", "horaFin", "13:00"));
        mockMvc.perform(post(availabilityUrl(employeeA2.getId()))
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeUserWithoutLinkedEmployeeGets403() throws Exception {
        String unlinkedToken = createUserAndLogin(
                companyAId, "sin-empleado@empresa-a.com", RolUsuario.EMPLOYEE, null, null);

        mockMvc.perform(get(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + unlinkedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorReadsOnlyEmployeesFromAssignedBranches() throws Exception {
        // supervisor asignado solo a branchA1: employeeA1 sí, employeeA2 no
        String supervisorToken = createUserAndLogin(
                companyAId, "supervisor@empresa-a.com", RolUsuario.SUPERVISOR, null, branchA1);

        createFranja(adminAToken, employeeA1.getId(), "LUNES", "09:00", "13:00");

        mockMvc.perform(get(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get(availabilityUrl(employeeA2.getId()))
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void supervisorCannotWrite() throws Exception {
        String supervisorToken = createUserAndLogin(
                companyAId, "supervisor@empresa-a.com", RolUsuario.SUPERVISOR, null, branchA1);

        String body = objectMapper.writeValueAsString(Map.of(
                "diaSemana", "LUNES", "horaInicio", "09:00", "horaFin", "13:00"));
        mockMvc.perform(post(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantEmployeeReturns404() throws Exception {
        Branch branchB = createBranch(companyBId, "Sucursal B");
        Employee employeeB = createEmployee(companyBId, branchB);

        mockMvc.perform(get(availabilityUrl(employeeB.getId()))
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void franjaOfAnotherEmployeeUnderWrongEmployeeIdReturns404() throws Exception {
        String franjaDeA1 = createFranja(adminAToken, employeeA1.getId(), "LUNES", "09:00", "13:00");

        // franja existe, pero pertenece a employeeA1 — bajo employeeA2 no se encuentra
        mockMvc.perform(patch(availabilityUrl(employeeA2.getId()) + "/" + franjaDeA1)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("horaFin", "15:00"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void overlappingFranjaSameDayReturns409() throws Exception {
        createFranja(adminAToken, employeeA1.getId(), "LUNES", "09:00", "17:00");

        String body = objectMapper.writeValueAsString(Map.of(
                "diaSemana", "LUNES", "horaInicio", "15:00", "horaFin", "20:00"));
        mockMvc.perform(post(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void adjacentFranjasDoNotConflict() throws Exception {
        createFranja(adminAToken, employeeA1.getId(), "LUNES", "09:00", "12:00");
        // intervalos [inicio, fin): 12:00 pega justo, no solapa
        createFranja(adminAToken, employeeA1.getId(), "LUNES", "12:00", "15:00");
    }

    @Test
    void midnightWrapIsAcceptedAndOverlapsAreDetected() throws Exception {
        // viernes 20:00–02:00: cruza medianoche, válida
        createFranja(adminAToken, employeeA1.getId(), "VIERNES", "20:00", "02:00");

        // 22:00–03:00 también wrappea y solapa con la anterior → 409
        String body = objectMapper.writeValueAsString(Map.of(
                "diaSemana", "VIERNES", "horaInicio", "22:00", "horaFin", "03:00"));
        mockMvc.perform(post(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void updateOverlappingAnotherFranjaReturns409ButKeepingOwnRangeIsAllowed() throws Exception {
        String franjaManana = createFranja(adminAToken, employeeA1.getId(), "LUNES", "09:00", "12:00");
        createFranja(adminAToken, employeeA1.getId(), "LUNES", "14:00", "18:00");

        // agrandar la franja de la mañana hasta pisar la de la tarde → 409
        mockMvc.perform(patch(availabilityUrl(employeeA1.getId()) + "/" + franjaManana)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("horaFin", "15:00"))))
                .andExpect(status().isConflict());

        // achicarla dentro de su propio rango (se excluye a sí misma del chequeo) → 200
        mockMvc.perform(patch(availabilityUrl(employeeA1.getId()) + "/" + franjaManana)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("horaFin", "11:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaFin").value("11:00:00"));
    }

    @Test
    void emptyFranjaReturns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "diaSemana", "LUNES", "horaInicio", "10:00", "horaFin", "10:00"));
        mockMvc.perform(post(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void secondsInHoraAreTruncatedToMinute() throws Exception {
        // horas con segundos no nulos: deben persistirse truncadas a minuto
        String body = objectMapper.writeValueAsString(Map.of(
                "diaSemana", "LUNES", "horaInicio", "09:00:15", "horaFin", "10:00:45"));
        mockMvc.perform(post(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horaInicio").value("09:00:00"))
                .andExpect(jsonPath("$.horaFin").value("10:00:00"));
    }

    @Test
    void secondsOnlyDifferenceIsRejectedAsEmptyFranjaAfterTruncation() throws Exception {
        // 09:00:30 y 09:00:00 difieren solo en segundos: antes del fix,
        // enMinutos descartaba los segundos y el wrap de medianoche
        // convertía esto en una franja "24hs" espuria en vez de rechazarla
        String body = objectMapper.writeValueAsString(Map.of(
                "diaSemana", "LUNES", "horaInicio", "09:00:30", "horaFin", "09:00:00"));
        mockMvc.perform(post(availabilityUrl(employeeA1.getId()))
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
