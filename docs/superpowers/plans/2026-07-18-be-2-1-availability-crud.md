# BE-2.1 — CRUD `EmployeeAvailability` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the availability CRUD (`/employees/{employeeId}/availability`) — weekly recurring time slots declared by the employee, readable by RRHH/Supervisor — as the first Fase 2 backend module.

**Architecture:** New domain module `backend/availability/` mirroring `branch/`: JPA entity extending `TenantAwareEntity` + Flyway migration, repository with explicit `companyId` methods, service with role scoping (EMPLOYEE self-only → 403, SUPERVISOR read-only scoped by JWT branchIds → 404, cross-tenant → 404) and domain validations (empty slot → 400, same-day overlap with midnight-wrap semantics → 409), thin controller with `@PreAuthorize`.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA (H2 dev/test, PostgreSQL prod), Flyway, Spring Security + JWT, JUnit 5 + MockMvc (`@SpringBootTest` + `@Transactional`, two-company seed pattern).

**Reference:** Spec at `docs/superpowers/specs/2026-07-18-be-2-1-availability-crud-design.md`. Issue [#49](https://github.com/BautistaChamorroD/Staffly/issues/49). Branch `feature/availability-crud` (already created from `main`, spec committed).

## Global Constraints

- `company_id` NUNCA se toma de la URL, el body, ni ningún input del cliente — siempre del JWT vía `StafflyUserPrincipal` (regla dura de `backend/CLAUDE.md`).
- Recurso de otro tenant → 404 `RESOURCE_NOT_FOUND`, nunca 403. EMPLOYEE pidiendo el recurso de OTRO empleado de su misma empresa → 403 `ACCESS_DENIED` (nota RF-29 de `docs/api-design.md`).
- Errores vía las excepciones existentes de `common/`: `ResourceNotFoundException` (404), `ConflictException` (409), `BadRequestException` (400 `VALIDATION_ERROR`), `org.springframework.security.access.AccessDeniedException` (403). `GlobalExceptionHandler` ya las mapea — no crear handlers nuevos.
- Campos y tablas en español (`dia_semana`, `hora_inicio`); clases/métodos Java en inglés salvo términos de dominio.
- `hora_fin < hora_inicio` es VÁLIDO y significa cruce de medianoche (wrap +24h). `hora_inicio == hora_fin` → 400.
- Solapamiento entre franjas del mismo `dia_semana` del mismo empleado → 409 (en PATCH, excluyéndose a sí misma). El edge del wrap contra el día siguiente NO se chequea (decisión de spec).
- Sin campos `estado` ni `tipo` en la entidad (desvíos deliberados documentados en el spec — no "completarlos").
- Lookups por id SIEMPRE vía `findByIdAndCompanyId` (el `tenantFilter` de Hibernate no cubre `findById`, ver `TenantAwareEntity`).
- Commits: Conventional Commits, mensajes en español, minúsculas, sin punto final.
- Rama: `feature/availability-crud`, ya creada con el spec commiteado. Los commits de este plan van encima.

---

## File Structure

```
backend/src/main/resources/db/migration/
└── V6__create_employee_availability.sql                     [CREATE]

backend/src/main/java/com/staffly/backend/availability/
├── DiaSemana.java                                           [CREATE] enum LUNES..DOMINGO
├── EmployeeAvailability.java                                [CREATE] entidad tenant-aware
├── AvailabilityRepository.java                              [CREATE]
├── AvailabilityService.java                                 [CREATE] scoping + validaciones + CRUD
├── AvailabilityController.java                              [CREATE]
└── dto/
    ├── AvailabilityResponse.java                            [CREATE]
    ├── CreateAvailabilityRequest.java                       [CREATE]
    └── UpdateAvailabilityRequest.java                       [CREATE]

backend/src/test/java/com/staffly/backend/availability/
└── AvailabilityControllerTest.java                          [CREATE]
```

Task order: modelo/persistencia (la migración y la entidad tienen que existir para que el test de Task 2 compile) → CRUD completo con TDD → verificación final.

---

### Task 1: Migración + enum + entidad + repositorio

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_employee_availability.sql`
- Create: `backend/src/main/java/com/staffly/backend/availability/DiaSemana.java`
- Create: `backend/src/main/java/com/staffly/backend/availability/EmployeeAvailability.java`
- Create: `backend/src/main/java/com/staffly/backend/availability/AvailabilityRepository.java`

**Interfaces:**
- Consumes: `TenantAwareEntity` (`tenant/`), `Employee` (`employee/`) — ambos existen desde Fase 1.
- Produces: enum `DiaSemana { LUNES, MARTES, MIERCOLES, JUEVES, VIERNES, SABADO, DOMINGO }`; entidad `EmployeeAvailability` con getters/setters `getId()`, `getEmployee()/setEmployee(Employee)`, `getDiaSemana()/setDiaSemana(DiaSemana)`, `getHoraInicio()/setHoraInicio(LocalTime)`, `getHoraFin()/setHoraFin(LocalTime)` (más `getCompanyId()/setCompanyId(UUID)` heredados); `AvailabilityRepository` con `findByIdAndCompanyId(UUID, UUID)`, `findByCompanyIdAndEmployeeId(UUID, UUID)`, `findByCompanyIdAndEmployeeIdAndDiaSemana(UUID, UUID, DiaSemana)`. Task 2 los consume todos.

- [ ] **Step 1: Escribir la migración**

Create `backend/src/main/resources/db/migration/V6__create_employee_availability.sql`:

```sql
CREATE TABLE employee_availability (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    employee_id UUID NOT NULL REFERENCES employee (id),
    dia_semana VARCHAR(10) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL
);

CREATE INDEX idx_employee_availability_employee ON employee_availability (employee_id);
```

- [ ] **Step 2: Escribir el enum**

Create `backend/src/main/java/com/staffly/backend/availability/DiaSemana.java`:

```java
package com.staffly.backend.availability;

/**
 * Día de la semana de una franja de disponibilidad recurrente. El orden de
 * declaración (LUNES primero) es el orden semanal que usa el service para
 * ordenar el listado — se persiste como STRING, así que un ORDER BY en SQL
 * sería alfabético y no sirve.
 */
public enum DiaSemana {
    LUNES,
    MARTES,
    MIERCOLES,
    JUEVES,
    VIERNES,
    SABADO,
    DOMINGO
}
```

- [ ] **Step 3: Escribir la entidad**

Create `backend/src/main/java/com/staffly/backend/availability/EmployeeAvailability.java`:

```java
package com.staffly.backend.availability;

import com.staffly.backend.employee.Employee;
import com.staffly.backend.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Franja de disponibilidad semanal recurrente declarada por el empleado
 * (RF-08, sin aprobación). Solo recurrencia semanal — sin excepciones
 * puntuales por fecha ni campo estado/tipo, ver decisiones del spec de
 * BE-2.1. hora_fin menor que hora_inicio significa que la franja cruza
 * medianoche (viernes 20:00–02:00 llega hasta el sábado 02:00).
 */
@Entity
@Table(name = "employee_availability")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class EmployeeAvailability extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DiaSemana diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    public UUID getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public DiaSemana getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(DiaSemana diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }
}
```

- [ ] **Step 4: Escribir el repositorio**

Create `backend/src/main/java/com/staffly/backend/availability/AvailabilityRepository.java`:

```java
package com.staffly.backend.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityRepository extends JpaRepository<EmployeeAvailability, UUID> {

    /**
     * Lookup explícito por id + company_id: el tenantFilter de Hibernate no
     * cubre findById (ver TenantAwareEntity).
     */
    Optional<EmployeeAvailability> findByIdAndCompanyId(UUID id, UUID companyId);

    List<EmployeeAvailability> findByCompanyIdAndEmployeeId(UUID companyId, UUID employeeId);

    List<EmployeeAvailability> findByCompanyIdAndEmployeeIdAndDiaSemana(
            UUID companyId, UUID employeeId, DiaSemana diaSemana);
}
```

- [ ] **Step 5: Verificar que la migración aplica y el contexto levanta**

Run: `cd backend && ./mvnw test -Dtest=BackendApplicationTests`
Expected: PASS — Flyway aplica V6 sobre H2 sin errores y el contexto de Spring levanta con la entidad nueva mapeada.

- [ ] **Step 6: Commit**

```bash
cd backend
git add src/main/resources/db/migration/V6__create_employee_availability.sql src/main/java/com/staffly/backend/availability
git commit -m "feat: agregar modelo y persistencia de disponibilidad"
```

---

### Task 2: DTOs + `AvailabilityService` + `AvailabilityController` (TDD)

**Files:**
- Test: `backend/src/test/java/com/staffly/backend/availability/AvailabilityControllerTest.java`
- Create: `backend/src/main/java/com/staffly/backend/availability/dto/AvailabilityResponse.java`
- Create: `backend/src/main/java/com/staffly/backend/availability/dto/CreateAvailabilityRequest.java`
- Create: `backend/src/main/java/com/staffly/backend/availability/dto/UpdateAvailabilityRequest.java`
- Create: `backend/src/main/java/com/staffly/backend/availability/AvailabilityService.java`
- Create: `backend/src/main/java/com/staffly/backend/availability/AvailabilityController.java`

**Interfaces:**
- Consumes: Task 1 (`DiaSemana`, `EmployeeAvailability`, `AvailabilityRepository`); de Fase 1: `EmployeeRepository.findByIdAndCompanyId`, `UserRepository.findByIdAndCompanyId`, `User.getEmployee()`, `StafflyUserPrincipal` (`getUserId()`, `getCompanyId()`, `getRol()`, `getBranchIds()`), `Rol` (`security/`), `ResourceNotFoundException`, `ConflictException`, `BadRequestException`.
- Produces: los 4 endpoints REST del contrato (`GET/POST /api/v1/employees/{employeeId}/availability`, `PATCH/DELETE .../{id}`). FE-2.1 y BE-2.3 consumen esta API y esta tabla.

- [ ] **Step 1: Escribir el test que falla**

Create `backend/src/test/java/com/staffly/backend/availability/AvailabilityControllerTest.java`:

```java
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
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && ./mvnw test -Dtest=AvailabilityControllerTest`
Expected: FAIL — error de compilación o 404 en todos los endpoints (`AvailabilityController` no existe todavía). Si falla la compilación del test por otro motivo (imports, helpers), corregir el test primero: el RED válido es "los endpoints no existen".

- [ ] **Step 3: Escribir los DTOs**

Create `backend/src/main/java/com/staffly/backend/availability/dto/AvailabilityResponse.java`:

```java
package com.staffly.backend.availability.dto;

import com.staffly.backend.availability.DiaSemana;
import com.staffly.backend.availability.EmployeeAvailability;

import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityResponse(UUID id, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin) {

    public static AvailabilityResponse from(EmployeeAvailability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getDiaSemana(),
                availability.getHoraInicio(),
                availability.getHoraFin());
    }
}
```

Create `backend/src/main/java/com/staffly/backend/availability/dto/CreateAvailabilityRequest.java`:

```java
package com.staffly.backend.availability.dto;

import com.staffly.backend.availability.DiaSemana;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CreateAvailabilityRequest(
        @NotNull DiaSemana diaSemana,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin) {
}
```

Create `backend/src/main/java/com/staffly/backend/availability/dto/UpdateAvailabilityRequest.java`:

```java
package com.staffly.backend.availability.dto;

import com.staffly.backend.availability.DiaSemana;

import java.time.LocalTime;

/**
 * Actualización parcial: campos nulos se dejan sin tocar (mismo patrón que
 * el resto de los PATCH del sistema).
 */
public record UpdateAvailabilityRequest(DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin) {
}
```

- [ ] **Step 4: Escribir el service**

Create `backend/src/main/java/com/staffly/backend/availability/AvailabilityService.java`:

```java
package com.staffly.backend.availability;

import com.staffly.backend.availability.dto.AvailabilityResponse;
import com.staffly.backend.availability.dto.CreateAvailabilityRequest;
import com.staffly.backend.availability.dto.UpdateAvailabilityRequest;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public AvailabilityService(
            AvailabilityRepository availabilityRepository,
            EmployeeRepository employeeRepository,
            UserRepository userRepository) {
        this.availabilityRepository = availabilityRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> list(UUID employeeId, StafflyUserPrincipal principal) {
        Employee employee = resolveEmployee(employeeId, principal);
        return availabilityRepository.findByCompanyIdAndEmployeeId(principal.getCompanyId(), employee.getId()).stream()
                // orden semanal (LUNES primero) por ordinal del enum: se
                // persiste como STRING, un ORDER BY en SQL sería alfabético
                .sorted(Comparator
                        .comparing((EmployeeAvailability a) -> a.getDiaSemana().ordinal())
                        .thenComparing(EmployeeAvailability::getHoraInicio))
                .map(AvailabilityResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AvailabilityResponse create(UUID employeeId, CreateAvailabilityRequest request, StafflyUserPrincipal principal) {
        Employee employee = resolveEmployee(employeeId, principal);
        validarFranja(request.horaInicio(), request.horaFin());
        validarSolape(employee, request.diaSemana(), request.horaInicio(), request.horaFin(), null, principal);

        EmployeeAvailability availability = new EmployeeAvailability();
        availability.setCompanyId(principal.getCompanyId());
        availability.setEmployee(employee);
        availability.setDiaSemana(request.diaSemana());
        availability.setHoraInicio(request.horaInicio());
        availability.setHoraFin(request.horaFin());
        return AvailabilityResponse.from(availabilityRepository.save(availability));
    }

    @Transactional
    public AvailabilityResponse update(
            UUID employeeId, UUID id, UpdateAvailabilityRequest request, StafflyUserPrincipal principal) {
        Employee employee = resolveEmployee(employeeId, principal);
        EmployeeAvailability availability = findFranjaOrThrow(id, employee, principal);

        // estado final = valor entrante o el guardado, según qué llegue
        DiaSemana diaFinal = request.diaSemana() != null ? request.diaSemana() : availability.getDiaSemana();
        LocalTime inicioFinal = request.horaInicio() != null ? request.horaInicio() : availability.getHoraInicio();
        LocalTime finFinal = request.horaFin() != null ? request.horaFin() : availability.getHoraFin();

        validarFranja(inicioFinal, finFinal);
        validarSolape(employee, diaFinal, inicioFinal, finFinal, availability.getId(), principal);

        availability.setDiaSemana(diaFinal);
        availability.setHoraInicio(inicioFinal);
        availability.setHoraFin(finFinal);
        return AvailabilityResponse.from(availabilityRepository.save(availability));
    }

    @Transactional
    public void delete(UUID employeeId, UUID id, StafflyUserPrincipal principal) {
        Employee employee = resolveEmployee(employeeId, principal);
        EmployeeAvailability availability = findFranjaOrThrow(id, employee, principal);
        availabilityRepository.delete(availability);
    }

    /**
     * Resuelve el empleado del path aplicando las tres capas de scoping:
     * tenant (otra empresa → 404), EMPLOYEE solo su propio registro (otro
     * empleado → 403, nota RF-29 de api-design), SUPERVISOR solo empleados
     * de sus sucursales asignadas (fuera de alcance → 404, mismo criterio
     * que EmployeeService).
     */
    private Employee resolveEmployee(UUID employeeId, StafflyUserPrincipal principal) {
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado solicitado"));

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmployeeId = userRepository.findByIdAndCompanyId(principal.getUserId(), principal.getCompanyId())
                    .map(User::getEmployee)
                    .map(Employee::getId)
                    .orElse(null);
            if (!employee.getId().equals(ownEmployeeId)) {
                throw new AccessDeniedException("Solo podés acceder a tu propia disponibilidad");
            }
        }

        if (principal.getRol() == Rol.SUPERVISOR
                && employee.getBranches().stream().noneMatch(b -> principal.getBranchIds().contains(b.getId()))) {
            throw new ResourceNotFoundException("No se encontró el empleado solicitado");
        }

        return employee;
    }

    private EmployeeAvailability findFranjaOrThrow(UUID id, Employee employee, StafflyUserPrincipal principal) {
        EmployeeAvailability availability = availabilityRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la franja solicitada"));
        if (!availability.getEmployee().getId().equals(employee.getId())) {
            // la franja existe pero pertenece a otro empleado: bajo este
            // employeeId no se encuentra
            throw new ResourceNotFoundException("No se encontró la franja solicitada");
        }
        return availability;
    }

    private void validarFranja(LocalTime horaInicio, LocalTime horaFin) {
        if (horaInicio.equals(horaFin)) {
            throw new BadRequestException("La franja no puede empezar y terminar a la misma hora");
        }
    }

    private void validarSolape(
            Employee employee, DiaSemana dia, LocalTime inicio, LocalTime fin, UUID excludeId,
            StafflyUserPrincipal principal) {
        int nuevoInicio = enMinutos(inicio);
        int nuevoFin = finEnMinutos(inicio, fin);
        for (EmployeeAvailability existente : availabilityRepository
                .findByCompanyIdAndEmployeeIdAndDiaSemana(principal.getCompanyId(), employee.getId(), dia)) {
            if (existente.getId().equals(excludeId)) {
                continue;
            }
            int inicioExistente = enMinutos(existente.getHoraInicio());
            int finExistente = finEnMinutos(existente.getHoraInicio(), existente.getHoraFin());
            if (nuevoInicio < finExistente && inicioExistente < nuevoFin) {
                throw new ConflictException("La franja se solapa con otra ya cargada para ese día");
            }
        }
    }

    private int enMinutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    /**
     * Fin del intervalo [inicio, fin) en minutos, con wrap: si la hora de
     * fin es menor o igual a la de inicio, la franja cruza medianoche y el
     * fin se corre +24h (viernes 20:00–02:00 → [1200, 1560)).
     */
    private int finEnMinutos(LocalTime inicio, LocalTime fin) {
        int minutosFin = enMinutos(fin);
        return minutosFin <= enMinutos(inicio) ? minutosFin + 24 * 60 : minutosFin;
    }
}
```

- [ ] **Step 5: Escribir el controller**

Create `backend/src/main/java/com/staffly/backend/availability/AvailabilityController.java`:

```java
package com.staffly.backend.availability;

import com.staffly.backend.availability.dto.AvailabilityResponse;
import com.staffly.backend.availability.dto.CreateAvailabilityRequest;
import com.staffly.backend.availability.dto.UpdateAvailabilityRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees/{employeeId}/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE')")
    public ResponseEntity<List<AvailabilityResponse>> list(
            @PathVariable UUID employeeId, @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(availabilityService.list(employeeId, principal));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<AvailabilityResponse> create(
            @PathVariable UUID employeeId,
            @Valid @RequestBody CreateAvailabilityRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        AvailabilityResponse response = availabilityService.create(employeeId, request, principal);
        return ResponseEntity
                .created(URI.create("/api/v1/employees/" + employeeId + "/availability/" + response.id()))
                .body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<AvailabilityResponse> update(
            @PathVariable UUID employeeId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAvailabilityRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(availabilityService.update(employeeId, id, request, principal));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID employeeId,
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        availabilityService.delete(employeeId, id, principal);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 6: Correr el test y verificar que pasa**

Run: `cd backend && ./mvnw test -Dtest=AvailabilityControllerTest`
Expected: PASS, 13 tests.

- [ ] **Step 7: Correr la suite completa**

Run: `cd backend && ./mvnw test`
Expected: PASS — 66 preexistentes + 13 nuevos = **79 tests**, cero regresiones.

- [ ] **Step 8: Commit**

```bash
cd backend
git add src/main/java/com/staffly/backend/availability src/test/java/com/staffly/backend/availability
git commit -m "feat: agregar crud de disponibilidad con validaciones y scoping por rol"
```

---

### Task 3: Verificación final contra el backend real

**Files:** none — verification only.

- [ ] **Step 1: Suite completa**

Run: `cd backend && ./mvnw test`
Expected: PASS, 79/79.

- [ ] **Step 2: Verificación manual liviana**

Levantar el backend (`cd backend && ./mvnw spring-boot:run`, perfil `dev`, H2 en memoria). Sembrar vía `/h2-console` (misma técnica fetch de FE-1.4/1.5/1.6, hash BCrypt generado con jshell o reusando uno conocido): una `company`, una `branch`, un `employee` vinculado a la branch, y dos `app_user` — un `ADMIN` y un `EMPLOYEE` con `employee_id` apuntando al empleado.

Con `curl` (o fetch desde la consola del navegador):

1. Login como EMPLOYEE → `POST /api/v1/auth/login` → guardar `accessToken`.
2. `POST /api/v1/employees/{employeeId}/availability` con `{"diaSemana":"LUNES","horaInicio":"09:00","horaFin":"17:00"}` → 201.
3. `POST` con `{"diaSemana":"VIERNES","horaInicio":"20:00","horaFin":"02:00"}` → 201 (cruce de medianoche real).
4. `GET .../availability` → 200, dos franjas, LUNES primero.
5. `POST` con `{"diaSemana":"LUNES","horaInicio":"10:00","horaFin":"12:00"}` → 409 con `code: CONFLICT`.
6. Login como ADMIN → `GET .../availability` del mismo empleado → 200 (lectura cruzada de rol OK).
7. Verificar en `/h2-console`: `SELECT * FROM employee_availability` — `company_id` poblado correcto en todas las filas.

- [ ] **Step 3: Commit final solo si hubo fixes**

Si la verificación manual destapó un bug, corregirlo con test de regresión primero y commitear:

```bash
cd backend
git add -A
git commit -m "fix: corregir <lo que haya fallado en la verificacion manual>"
```

Si no hubo nada que corregir, la rama queda lista para PR (`Closes #49`).

---

## Self-Review Notes

- **Spec coverage:** modelo sin `estado`/`tipo` (Task 1), los 4 endpoints del contrato §6 (Task 2 controller), las 4 capas de autorización (Task 2 service: `@PreAuthorize` + tenant 404 + EMPLOYEE 403 + SUPERVISOR 404), franja vacía 400 / wrap válido / solape 409 con exclusión en PATCH (Task 2 `validarFranja`/`validarSolape`), orden LUNES-primero en el service (Task 2 `list`), y la matriz de testing completa del spec mapea 1:1 a los 13 tests de `AvailabilityControllerTest`.
- **Type consistency:** `DiaSemana`/`EmployeeAvailability`/`AvailabilityRepository` (Task 1) se consumen con esos nombres exactos en Task 2; los DTOs usan `LocalTime` en requests y responses; los métodos del repositorio nombrados idéntico en la definición (Task 1) y en el service (Task 2). Los helpers del test usan las mismas firmas de entidades de Fase 1 que `EmployeeControllerTest`/`TenantIsolationTest` (verificado contra el código real, incluyendo `documento` único por empresa post BE-1.10 — por eso el `documentoSeq`).
- **No placeholders:** todos los pasos tienen código completo y ejecutable; la verificación manual lista acciones concretas con resultados esperados.
