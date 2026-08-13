// backend/src/test/java/com/staffly/backend/schedule/ScheduleControllerTest.java
package com.staffly.backend.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.availability.AvailabilityRepository;
import com.staffly.backend.availability.DiaSemana;
import com.staffly.backend.availability.EmployeeAvailability;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;
import com.staffly.backend.common.audit.AuditLog;
import com.staffly.backend.common.audit.AuditLogRepository;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScheduleControllerTest {

    private static final String BASE_URL = "/api/v1/schedules";
    private static final String PASSWORD = "Password123";
    private static final AtomicInteger docSeq = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager em;
    @Autowired private UserRepository userRepository;
    @Autowired private AvailabilityRepository availabilityRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Company A
    private UUID companyAId;
    private Branch branch1; // supervisor manages
    private Branch branch2; // supervisor does NOT manage
    private Employee emp1;  // assigned to branch1
    private Employee emp2;  // assigned to branch2
    private String adminToken;
    private String rrhhToken;
    private String supervisorToken; // manages branch1 only
    private String empToken1;       // linked to emp1

    // Company B
    private UUID companyBId;
    private Branch branchB;
    private Employee empB;
    private String adminBToken;

    @BeforeEach
    void setUp() throws Exception {
        companyAId = createCompany("Empresa A");
        branch1 = createBranch(companyAId, "Sucursal 1");
        branch2 = createBranch(companyAId, "Sucursal 2");
        emp1 = createEmployee(companyAId, branch1);
        emp2 = createEmployee(companyAId, branch2);

        adminToken    = createUserAndLogin(companyAId, "admin@a.com",      RolUsuario.ADMIN,      null,  null);
        rrhhToken     = createUserAndLogin(companyAId, "rrhh@a.com",       RolUsuario.RRHH,       null,  null);
        supervisorToken = createUserAndLogin(companyAId, "sup@a.com",      RolUsuario.SUPERVISOR, null,  branch1);
        empToken1     = createUserAndLogin(companyAId, "emp1@a.com",       RolUsuario.EMPLOYEE,   emp1,  null);

        companyBId = createCompany("Empresa B");
        branchB    = createBranch(companyBId, "Sucursal B");
        empB       = createEmployee(companyBId, branchB);
        adminBToken = createUserAndLogin(companyBId, "admin@b.com",        RolUsuario.ADMIN,      null,  null);
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
        em.persist(c);
        em.flush();
        return c.getId();
    }

    private Branch createBranch(UUID companyId, String nombre) {
        Branch b = new Branch();
        b.setCompanyId(companyId);
        b.setNombre(nombre);
        b.setDireccion("Dirección");
        b.setZonaHoraria("America/Argentina/Buenos_Aires");
        b.setEstado(EstadoSucursal.ACTIVA);
        em.persist(b);
        em.flush();
        return b;
    }

    private Employee createEmployee(UUID companyId, Branch branch) {
        Employee e = new Employee();
        e.setCompanyId(companyId);
        e.setNombre("Empleado");
        e.setApellido("Test" + docSeq.get());
        e.setDocumento("300000" + docSeq.getAndIncrement());
        e.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        e.setFechaIngreso(LocalDate.of(2024, 1, 1));
        e.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        e.setCategoria("Vendedor");
        e.setSueldoBase(new BigDecimal("500000"));
        e.setEstadoLaboral(EstadoLaboral.ACTIVO);
        e.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        e.getBranches().add(branch);
        em.persist(e);
        em.flush();
        return e;
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol, Employee employee, Branch assignedBranch) throws Exception {
        User u = new User();
        u.setCompanyId(companyId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRol(rol);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setDebeCambiarPassword(false);
        if (employee != null) u.setEmployee(employee);
        if (assignedBranch != null) u.getBranches().add(assignedBranch);
        userRepository.save(u);
        em.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResp).get("accessToken").asText();
    }

    /** Crea un schedule y retorna su ID. Falla si la respuesta no es 201. */
    private UUID postSchedule(String token, UUID employeeId, UUID branchId,
                              String inicio, String fin, String tipo) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", employeeId.toString(),
                "branchId", branchId.toString(),
                "fechaHoraInicio", inicio,
                "fechaHoraFin", fin,
                "tipoTurno", tipo));
        String resp = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(resp).get("id").asText());
    }

    private void addAvailability(UUID companyId, Employee emp, DiaSemana dia, LocalTime inicio, LocalTime fin) {
        EmployeeAvailability a = new EmployeeAvailability();
        a.setCompanyId(companyId);
        a.setEmployee(emp);
        a.setDiaSemana(dia);
        a.setHoraInicio(inicio);
        a.setHoraFin(fin);
        em.persist(a);
        em.flush();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void adminCrudLifecycle() throws Exception {
        // POST → 201
        UUID id = postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // GET list → 1 resultado
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("PLANIFICADO"))
                .andExpect(jsonPath("$[0].horasTotales").value(8.0));

        // GET by id
        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoTurno").value("FIJO"));

        // PATCH → cambiar hora fin y tipo
        mockMvc.perform(patch(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaHoraFin", "2026-07-06T18:00:00",
                                "tipoTurno", "ROTATIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horasTotales").value(9.0))
                .andExpect(jsonPath("$.tipoTurno").value("ROTATIVO"));

        // confirm → CONFIRMADO
        mockMvc.perform(post(BASE_URL + "/" + id + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        // status → CUMPLIDO
        mockMvc.perform(patch(BASE_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estado", "CUMPLIDO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CUMPLIDO"));

        // DELETE en estado CUMPLIDO → 409
        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void solapamientoMismaSucursal() throws Exception {
        postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // turno solapado en la misma sucursal → 409
        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", emp1.getId().toString(),
                "branchId", branch1.getId().toString(),
                "fechaHoraInicio", "2026-07-06T12:00:00",
                "fechaHoraFin", "2026-07-06T20:00:00",
                "tipoTurno", "ROTATIVO"));
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void solapamientoCrossBranch() throws Exception {
        // emp1 tiene branch1; le asignamos también branch2 para este test
        emp1.getBranches().add(branch2);
        em.flush();

        postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // mismo empleado, sucursal distinta, horario solapado → 409
        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", emp1.getId().toString(),
                "branchId", branch2.getId().toString(),
                "fechaHoraInicio", "2026-07-06T14:00:00",
                "fechaHoraFin", "2026-07-06T22:00:00",
                "tipoTurno", "ROTATIVO"));
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void solapamientoCrossTenant() throws Exception {
        // schedule de emp1 (empresa A) no interfiere con empB (empresa B)
        postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // schedule de empB en el mismo horario → 201 (distinta empresa, sin conflicto)
        postSchedule(adminBToken, empB.getId(), branchB.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");
    }

    @Test
    void patchSelfExclusionSolapamiento() throws Exception {
        UUID id = postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // PATCH con los mismos timestamps → no debe dar 409
        mockMvc.perform(patch(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaHoraInicio", "2026-07-06T09:00:00",
                                "fechaHoraFin", "2026-07-06T17:00:00"))))
                .andExpect(status().isOk());
    }

    @Test
    void warningFueraDeDisponibilidad() throws Exception {
        // emp1 sin ninguna franja de disponibilidad → warning
        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", emp1.getId().toString(),
                "branchId", branch1.getId().toString(),
                "fechaHoraInicio", "2026-07-06T09:00:00",
                "fechaHoraFin", "2026-07-06T17:00:00",
                "tipoTurno", "FIJO"));
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warning").value("OUT_OF_AVAILABILITY"));
    }

    @Test
    void sinWarningDentroDeDisponibilidad() throws Exception {
        // 2026-07-06 es lunes — LUNES 08:00-18:00 cubre el turno 09:00-17:00
        addAvailability(companyAId, emp1, DiaSemana.LUNES, LocalTime.of(8, 0), LocalTime.of(18, 0));

        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", emp1.getId().toString(),
                "branchId", branch1.getId().toString(),
                "fechaHoraInicio", "2026-07-06T09:00:00",
                "fechaHoraFin", "2026-07-06T17:00:00",
                "tipoTurno", "FIJO"));
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warning").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void warningTurnoCruzaMedianoche() throws Exception {
        // Turno viernes 22:00 → sábado 06:00
        // Disponibilidad VIERNES 20:00-23:59 cubre segmento A pero no hay SABADO → warning
        // 2026-07-10 es viernes
        addAvailability(companyAId, emp1, DiaSemana.VIERNES, LocalTime.of(20, 0), LocalTime.of(23, 59));

        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", emp1.getId().toString(),
                "branchId", branch1.getId().toString(),
                "fechaHoraInicio", "2026-07-10T22:00:00",
                "fechaHoraFin", "2026-07-11T06:00:00",
                "tipoTurno", "ROTATIVO"));
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warning").value("OUT_OF_AVAILABILITY"));
    }

    @Test
    void auditLogCreadoConWarning() throws Exception {
        // sin disponibilidad → warning → debe haber un AuditLog con campo "asignacion_fuera_disponibilidad"
        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", emp1.getId().toString(),
                "branchId", branch1.getId().toString(),
                "fechaHoraInicio", "2026-07-06T09:00:00",
                "fechaHoraFin", "2026-07-06T17:00:00",
                "tipoTurno", "FIJO"));
        String resp = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID scheduleId = UUID.fromString(objectMapper.readTree(resp).get("id").asText());

        em.flush();
        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(l -> l.getEntityType().equals("Schedule")
                        && l.getEntityId().equals(scheduleId)
                        && l.getCampo().equals("asignacion_fuera_disponibilidad"))
                .toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getValorNuevo()).isEqualTo("OUT_OF_AVAILABILITY");
    }

    @Test
    void supervisorSoloVeSusSucursales() throws Exception {
        // emp1 en branch1 (scope del supervisor), emp2 en branch2 (fuera de scope)
        postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");
        postSchedule(adminToken, emp2.getId(), branch2.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // supervisor ve solo el de branch1
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchId").value(branch1.getId().toString()));
    }

    @Test
    void employeeSoloVeLosSuyos() throws Exception {
        // schedule de emp1 y schedule de emp2
        postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");
        postSchedule(adminToken, emp2.getId(), branch2.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // empToken1 solo ve el propio
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + empToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(emp1.getId().toString()));
    }

    @Test
    void employeeNoAccedeGetAjenoById() throws Exception {
        // schedule de emp2, empToken1 intenta verlo → 404
        UUID id = postSchedule(adminToken, emp2.getId(), branch2.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + empToken1))
                .andExpect(status().isNotFound());
    }

    @Test
    void empleadoFueraDeAlcanceSupervisor() throws Exception {
        // emp2 pertenece a branch2, supervisor solo gestiona branch1 → 404 al intentar crear para emp2
        String body = objectMapper.writeValueAsString(Map.of(
                "employeeId", emp2.getId().toString(),
                "branchId", branch2.getId().toString(),
                "fechaHoraInicio", "2026-07-06T09:00:00",
                "fechaHoraFin", "2026-07-06T17:00:00",
                "tipoTurno", "FIJO"));
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteEnPlanificado() throws Exception {
        UUID id = postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteNoEnPlanificado() throws Exception {
        UUID id = postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        mockMvc.perform(post(BASE_URL + "/" + id + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // DELETE en estado CONFIRMADO → 409
        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmTransicion() throws Exception {
        UUID id = postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // PLANIFICADO → CONFIRMADO
        mockMvc.perform(post(BASE_URL + "/" + id + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        // second confirm (ya está CONFIRMADO) → 409
        mockMvc.perform(post(BASE_URL + "/" + id + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void statusTransicion() throws Exception {
        UUID id = postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // desde PLANIFICADO → status → 409 (requiere CONFIRMADO primero)
        mockMvc.perform(patch(BASE_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estado", "CUMPLIDO"))))
                .andExpect(status().isConflict());

        // confirmar primero
        mockMvc.perform(post(BASE_URL + "/" + id + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // CONFIRMADO + target PLANIFICADO → 409
        mockMvc.perform(patch(BASE_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estado", "PLANIFICADO"))))
                .andExpect(status().isConflict());

        // CONFIRMADO → AUSENTE → 200
        mockMvc.perform(patch(BASE_URL + "/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("estado", "AUSENTE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AUSENTE"));
    }

    @Test
    void crossTenantIsolacion() throws Exception {
        UUID id = postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // admin de empresa B intenta ver schedule de empresa A → 404
        mockMvc.perform(get(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isNotFound());

        // admin de empresa B intenta borrar schedule de empresa A → 404
        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + adminBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtroDesdeHasta() throws Exception {
        // lunes 2026-07-06 y lunes 2026-07-13
        postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");
        postSchedule(adminToken, emp1.getId(), branch1.getId(),
                "2026-07-13T09:00:00", "2026-07-13T17:00:00", "FIJO");

        // filtrar solo semana del 06
        mockMvc.perform(get(BASE_URL + "?desde=2026-07-06&hasta=2026-07-09")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fechaHoraInicio").value("2026-07-06T09:00:00"));
    }

    @Test
    void rrhhPuedeEscribir() throws Exception {
        // RRHH puede crear
        UUID id = postSchedule(rrhhToken, emp1.getId(), branch1.getId(),
                "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

        // RRHH puede patchear
        mockMvc.perform(patch(BASE_URL + "/" + id)
                        .header("Authorization", "Bearer " + rrhhToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("tipoTurno", "ROTATIVO"))))
                .andExpect(status().isOk());

        // RRHH puede borrar
        mockMvc.perform(delete(BASE_URL + "/" + id).header("Authorization", "Bearer " + rrhhToken))
                .andExpect(status().isNoContent());
    }
}
