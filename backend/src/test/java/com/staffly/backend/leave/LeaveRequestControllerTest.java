package com.staffly.backend.leave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.company.Company;
import com.staffly.backend.company.EstadoEmpresa;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
import com.staffly.backend.schedule.EstadoTurno;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.TipoTurno;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaveRequestControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/leave-requests";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private UUID companyBId;
    private Branch branchA1;
    private Branch branchA2;
    private Employee empA1;
    private Employee empA2;
    private LeaveType leaveTypeA;
    private String adminAToken;

    @BeforeEach
    void seed() throws Exception {
        companyAId = createCompany("Empresa A");
        branchA1 = createBranch(companyAId, "Sucursal A1");
        branchA2 = createBranch(companyAId, "Sucursal A2");
        empA1 = createEmployee(companyAId, branchA1, "Juan", "Pérez");
        empA2 = createEmployee(companyAId, branchA2, "Ana", "García");
        leaveTypeA = createLeaveType(companyAId, "Vacaciones");
        adminAToken = createUserAndLogin(companyAId, "admin-a@empresa-a.com", RolUsuario.ADMIN, null, null);
        companyBId = createCompany("Empresa B");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID createCompany(String nombre) {
        Company c = new Company();
        c.setNombre(nombre); c.setRazonSocial(nombre + " SRL");
        c.setPais("AR"); c.setMoneda("ARS");
        c.setZonaHoraria("America/Argentina/Buenos_Aires");
        c.setEstado(EstadoEmpresa.ACTIVA);
        entityManager.persist(c); entityManager.flush();
        return c.getId();
    }

    private Branch createBranch(UUID companyId, String nombre) {
        Branch b = new Branch();
        b.setCompanyId(companyId); b.setNombre(nombre);
        b.setDireccion("Dir"); b.setZonaHoraria("America/Argentina/Buenos_Aires");
        b.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(b); entityManager.flush();
        return b;
    }

    private Employee createEmployee(UUID companyId, Branch branch, String nombre, String apellido) {
        Employee e = new Employee();
        e.setCompanyId(companyId); e.setNombre(nombre); e.setApellido(apellido);
        e.setDocumento(UUID.randomUUID().toString().substring(0, 8));
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setSueldoBase(BigDecimal.valueOf(100000));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("General");
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        e.getBranches().add(branch);
        entityManager.persist(e); entityManager.flush();
        return e;
    }

    private LeaveType createLeaveType(UUID companyId, String nombre) {
        LeaveType lt = new LeaveType();
        lt.setCompanyId(companyId); lt.setNombre(nombre); lt.setEsPaga(true);
        entityManager.persist(lt); entityManager.flush();
        return lt;
    }

    private Schedule createSchedule(UUID companyId, Employee employee, Branch branch,
                                    LocalDateTime inicio, LocalDateTime fin) {
        Schedule s = new Schedule();
        s.setCompanyId(companyId); s.setEmployee(employee); s.setBranch(branch);
        s.setFechaHoraInicio(inicio); s.setFechaHoraFin(fin);
        s.setTipoTurno(TipoTurno.FIJO); s.setEstado(EstadoTurno.PLANIFICADO);
        entityManager.persist(s); entityManager.flush();
        return s;
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol,
                                      Branch branch, Employee employee) throws Exception {
        User user = new User();
        user.setCompanyId(companyId); user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(rol); user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(false);
        if (branch != null) user.getBranches().add(branch);
        if (employee != null) user.setEmployee(employee);
        userRepository.save(user); entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String resp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("accessToken").asText();
    }

    private String createLeaveRequest(String token, UUID employeeId, String fechaInicio, String fechaFin)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (employeeId != null) body.put("employeeId", employeeId.toString());
        body.put("leaveTypeId", leaveTypeA.getId().toString());
        body.put("fechaInicio", fechaInicio);
        body.put("fechaFin", fechaFin);

        String resp = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("id").asText();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void adminCreaLicenciaParaEmpleado() throws Exception {
        String id = createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.employeeId").value(empA1.getId().toString()))
                .andExpect(jsonPath("$.employeeNombre").value("Juan"))
                .andExpect(jsonPath("$.employeeApellido").value("Pérez"))
                .andExpect(jsonPath("$.leaveTypeNombre").value("Vacaciones"))
                .andExpect(jsonPath("$.fechaInicio").value("2026-09-01"))
                .andExpect(jsonPath("$.fechaFin").value("2026-09-05"));
    }

    @Test
    void rrhhCreaLicenciaParaEmpleado() throws Exception {
        String rrhhToken = createUserAndLogin(companyAId, "rrhh@empresa-a.com", RolUsuario.RRHH, null, null);
        createLeaveRequest(rrhhToken, empA1.getId(), "2026-09-01", "2026-09-03");
    }

    @Test
    void empleadoCreaLicenciaPropia() throws Exception {
        String empToken = createUserAndLogin(companyAId, "emp@empresa-a.com", RolUsuario.EMPLOYEE, null, empA1);

        // EMPLOYEE no envía employeeId — lo resuelve el sistema
        Map<String, Object> body = Map.of(
                "leaveTypeId", leaveTypeA.getId().toString(),
                "fechaInicio", "2026-10-01",
                "fechaFin", "2026-10-05");

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + empToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value(empA1.getId().toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void adminSinEmployeeIdRetorna400() throws Exception {
        Map<String, Object> body = Map.of(
                "leaveTypeId", leaveTypeA.getId().toString(),
                "fechaInicio", "2026-09-01",
                "fechaFin", "2026-09-05");

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supervisorNoPuedeCrear() throws Exception {
        String supToken = createUserAndLogin(companyAId, "sup@empresa-a.com", RolUsuario.SUPERVISOR, branchA1, null);
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + supToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeId", empA1.getId().toString(),
                                "leaveTypeId", leaveTypeA.getId().toString(),
                                "fechaInicio", "2026-09-01",
                                "fechaFin", "2026-09-05"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarConFiltros() throws Exception {
        createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");
        createLeaveRequest(adminAToken, empA2.getId(), "2026-10-01", "2026-10-03");

        // sin filtros: ve todas
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // filtro por employeeId
        mockMvc.perform(get(BASE_URL + "?employeeId=" + empA1.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(empA1.getId().toString()));

        // filtro por estado PENDIENTE
        mockMvc.perform(get(BASE_URL + "?estado=PENDIENTE")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void empleadoSoloVeLosSuyos() throws Exception {
        String empToken = createUserAndLogin(companyAId, "emp@empresa-a.com", RolUsuario.EMPLOYEE, null, empA1);
        createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");
        createLeaveRequest(adminAToken, empA2.getId(), "2026-10-01", "2026-10-03");

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(empA1.getId().toString()));
    }

    @Test
    void empleadoNoVeDetalleDeOtro() throws Exception {
        String empToken = createUserAndLogin(companyAId, "emp@empresa-a.com", RolUsuario.EMPLOYEE, null, empA1);
        String idOtro = createLeaveRequest(adminAToken, empA2.getId(), "2026-10-01", "2026-10-03");

        mockMvc.perform(get(BASE_URL + "/" + idOtro).header("Authorization", "Bearer " + empToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void supervisorVeSusSucursales() throws Exception {
        String supToken = createUserAndLogin(companyAId, "sup@empresa-a.com", RolUsuario.SUPERVISOR, branchA1, null);
        createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05"); // branchA1
        createLeaveRequest(adminAToken, empA2.getId(), "2026-10-01", "2026-10-03"); // branchA2

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + supToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(empA1.getId().toString()));
    }

    @Test
    void approveHappyPath() throws Exception {
        String id = createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");

        mockMvc.perform(post(BASE_URL + "/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
    }

    @Test
    void approveConConflictoRetorna409() throws Exception {
        // turno del empleado que se superpone con la licencia
        createSchedule(companyAId, empA1, branchA1,
                LocalDateTime.of(2026, 9, 3, 8, 0),
                LocalDateTime.of(2026, 9, 3, 16, 0));

        String id = createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");

        mockMvc.perform(post(BASE_URL + "/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAVE_APPROVAL_CONFLICT"))
                .andExpect(jsonPath("$.turnosConflictivos").isArray())
                .andExpect(jsonPath("$.turnosConflictivos.length()").value(1));
    }

    @Test
    void approveYaAprobadaRetorna400() throws Exception {
        String id = createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");
        mockMvc.perform(post(BASE_URL + "/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectHappyPath() throws Exception {
        String id = createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");

        mockMvc.perform(post(BASE_URL + "/" + id + "/reject")
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("motivo", "Falta de personal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("Falta de personal"));
    }

    @Test
    void rejectSinMotivoRetorna400() throws Exception {
        String id = createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");

        mockMvc.perform(post(BASE_URL + "/" + id + "/reject")
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("motivo", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelPendienteByEmpleado() throws Exception {
        String empToken = createUserAndLogin(companyAId, "emp@empresa-a.com", RolUsuario.EMPLOYEE, null, empA1);
        String id = createLeaveRequest(adminAToken, empA1.getId(), "2026-09-01", "2026-09-05");

        mockMvc.perform(post(BASE_URL + "/" + id + "/cancel")
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelAprobadaNoIniciadaByAdmin() throws Exception {
        String id = createLeaveRequest(adminAToken, empA1.getId(), "2099-09-01", "2099-09-05");
        mockMvc.perform(post(BASE_URL + "/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/" + id + "/cancel")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelAprobadaYaIniciadaRetorna400() throws Exception {
        // fecha inicio en el pasado
        String id = createLeaveRequest(adminAToken, empA1.getId(), "2020-01-01", "2020-01-05");
        mockMvc.perform(post(BASE_URL + "/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/" + id + "/cancel")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void empleadoNoPuedeCancelarLaDeOtro() throws Exception {
        String empToken = createUserAndLogin(companyAId, "emp@empresa-a.com", RolUsuario.EMPLOYEE, null, empA1);
        String idOtro = createLeaveRequest(adminAToken, empA2.getId(), "2026-09-01", "2026-09-05");

        mockMvc.perform(post(BASE_URL + "/" + idOtro + "/cancel")
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void fechaFinAntesDeInicioRetorna400() throws Exception {
        Map<String, Object> body = Map.of(
                "employeeId", empA1.getId().toString(),
                "leaveTypeId", leaveTypeA.getId().toString(),
                "fechaInicio", "2026-09-10",
                "fechaFin", "2026-09-05");

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossTenantIsolation() throws Exception {
        // solicitud de empresa B creada directamente en BD
        Employee empB = createEmployee(companyBId, createBranch(companyBId, "Sucursal B1"), "Pedro", "López");
        LeaveType ltB = createLeaveType(companyBId, "Enfermedad");
        LeaveRequest lrB = new LeaveRequest();
        lrB.setCompanyId(companyBId);
        lrB.setEmployee(empB);
        lrB.setLeaveType(ltB);
        lrB.setFechaInicio(LocalDate.of(2026, 9, 1));
        lrB.setFechaFin(LocalDate.of(2026, 9, 5));
        entityManager.persist(lrB);
        entityManager.flush();

        // admin A no ve la de empresa B en el listado
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // admin A no puede ver el detalle de empresa B → 404
        mockMvc.perform(get(BASE_URL + "/" + lrB.getId())
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNotFound());

        // admin A no puede aprobar la de empresa B → 404
        mockMvc.perform(post(BASE_URL + "/" + lrB.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get(BASE_URL)).andExpect(status().isUnauthorized());
    }
}
