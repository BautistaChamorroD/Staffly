# Holiday CRUD (BE-2.2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar el CRUD de feriados (`Holiday`) con scoping multi-tenant, filtros de listado por año y sucursal, y validación de duplicados por ámbito.

**Architecture:** Módulo `holiday/` siguiendo el patrón establecido en `availability/`: entidad JPA extendiendo `TenantAwareEntity`, repositorio Spring Data, service con validación de negocio, controller con `@PreAuthorize`. Lógica de scoping de SUPERVISOR y deduplicación por (company_id, branch_id, fecha) resuelta a nivel de servicio.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, H2 en memoria (tests), JUnit 5 + MockMvc, Flyway (migración V7).

## Global Constraints

- `company_id` siempre del JWT (`principal.getCompanyId()`), nunca de la request.
- Lookups siempre con `findByIdAndCompanyId` — el `@Filter tenantFilter` de Hibernate no cubre `findById` directo.
- Nombres de columnas en español; nombres de clases/métodos Java en inglés.
- Conventional commits en español: `feat:`, `test:`, `docs:`, etc.
- Tests: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`. Usar `entityManager.flush()` después de cada `persist()`.
- IDs: `UUID` generados por la base (`@GeneratedValue(strategy = GenerationType.UUID)`).
- Sin `ON DELETE CASCADE` en las FK de la migración (consistente con V6).

---

## File Structure

```
backend/src/main/resources/db/migration/
  V7__create_holiday.sql                          ← nueva

backend/src/main/java/com/staffly/backend/holiday/
  Holiday.java                                    ← nueva (entidad)
  HolidayRepository.java                          ← nueva
  HolidayService.java                             ← nueva
  HolidayController.java                          ← nueva
  dto/
    HolidayResponse.java                          ← nueva
    CreateHolidayRequest.java                     ← nueva
    UpdateHolidayRequest.java                     ← nueva

backend/src/test/java/com/staffly/backend/holiday/
  HolidayControllerTest.java                      ← nueva
```

---

## Task 1: Migración + entidad + repositorio

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__create_holiday.sql`
- Create: `backend/src/main/java/com/staffly/backend/holiday/Holiday.java`
- Create: `backend/src/main/java/com/staffly/backend/holiday/HolidayRepository.java`
- Create: `backend/src/test/java/com/staffly/backend/holiday/HolidayControllerTest.java` (solo el esqueleto con un test RED)

**Interfaces:**
- Produces:
  - `Holiday` — entidad con getters: `getId()`, `getCompanyId()`, `getBranchId()` (UUID nullable), `getFecha()`, `getNombre()`, `isRecurrente()`, `getBranch()` (Branch nullable)
  - `HolidayRepository` — métodos: `findByIdAndCompanyId`, `findByCompanyId`, `findByCompanyIdAndFechaBetween`, `existsByCompanyIdAndBranchIdIsNullAndFecha`, `existsByCompanyIdAndBranchIdAndFecha`, `existsByCompanyIdAndBranchIdIsNullAndFechaAndIdNot`, `existsByCompanyIdAndBranchIdAndFechaAndIdNot`

---

- [ ] **Step 1: Escribir el test RED mínimo**

Crear el archivo de test con un único test que intente crear un feriado. Va a fallar con 500 (el GlobalExceptionHandler atrapa la ruta inexistente) o 404 — confirma que el sistema está en rojo antes de implementar.

```java
// backend/src/test/java/com/staffly/backend/holiday/HolidayControllerTest.java
package com.staffly.backend.holiday;

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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HolidayControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/holidays";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private UUID companyBId;
    private Branch branchA1;
    private Branch branchA2;
    private String adminAToken;

    @BeforeEach
    void seedTwoCompanies() throws Exception {
        companyAId = createCompany("Empresa A");
        branchA1 = createBranch(companyAId, "Sucursal A1");
        branchA2 = createBranch(companyAId, "Sucursal A2");
        adminAToken = createUserAndLogin(companyAId, "admin-a@empresa-a.com", RolUsuario.ADMIN, null);

        companyBId = createCompany("Empresa B");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private Branch createBranch(UUID companyId, String nombre) {
        Branch branch = new Branch();
        branch.setCompanyId(companyId);
        branch.setNombre(nombre);
        branch.setDireccion("Dirección");
        branch.setZonaHoraria("America/Argentina/Buenos_Aires");
        branch.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(branch);
        entityManager.flush();
        return branch;
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol, Branch assignedBranch)
            throws Exception {
        User user = new User();
        user.setCompanyId(companyId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(rol);
        user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(false);
        if (assignedBranch != null) user.getBranches().add(assignedBranch);
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

    private String createHoliday(String token, String fecha, String nombre, UUID branchId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("fecha", fecha);
        body.put("nombre", nombre);
        body.put("recurrente", false);
        if (branchId != null) body.put("branchId", branchId.toString());

        String response = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    // ── RED: único test inicial ───────────────────────────────────────────────

    @Test
    void redVerification_adminCreatesHoliday() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25",
                                "nombre", "Navidad",
                                "recurrente", true))))
                .andExpect(status().isCreated());
    }
}
```

- [ ] **Step 2: Ejecutar el test RED**

```bash
cd backend && ./mvnw test -pl . -Dtest=HolidayControllerTest#redVerification_adminCreatesHoliday -q
```

Resultado esperado: **FAIL** — `Expected: 201, was: 5xx` (controller no existe aún). Si falla de otra forma (compile error en test), corregir antes de seguir.

- [ ] **Step 3: Escribir la migración V7**

```sql
-- backend/src/main/resources/db/migration/V7__create_holiday.sql
CREATE TABLE holiday (
    id          UUID        NOT NULL,
    company_id  UUID        NOT NULL,
    branch_id   UUID,
    fecha       DATE        NOT NULL,
    nombre      VARCHAR(255) NOT NULL,
    recurrente  BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_holiday PRIMARY KEY (id),
    CONSTRAINT fk_holiday_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_holiday_branch  FOREIGN KEY (branch_id)  REFERENCES branch (id)
);

CREATE INDEX idx_holiday_company_fecha ON holiday (company_id, fecha);
```

- [ ] **Step 4: Escribir la entidad `Holiday`**

```java
// backend/src/main/java/com/staffly/backend/holiday/Holiday.java
package com.staffly.backend.holiday;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "holiday")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Holiday extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "branch_id", nullable = true)
    private Branch branch;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "recurrente", nullable = false)
    private boolean recurrente;

    public UUID getId() { return id; }

    public Branch getBranch() { return branch; }

    public void setBranch(Branch branch) { this.branch = branch; }

    /** Devuelve el UUID de la sucursal, o null si el feriado es global. */
    public UUID getBranchId() {
        return branch != null ? branch.getId() : null;
    }

    public LocalDate getFecha() { return fecha; }

    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getNombre() { return nombre; }

    public void setNombre(String nombre) { this.nombre = nombre; }

    public boolean isRecurrente() { return recurrente; }

    public void setRecurrente(boolean recurrente) { this.recurrente = recurrente; }
}
```

- [ ] **Step 5: Escribir `HolidayRepository`**

```java
// backend/src/main/java/com/staffly/backend/holiday/HolidayRepository.java
package com.staffly.backend.holiday;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    /**
     * Lookup por id + company_id: el tenantFilter de Hibernate no cubre
     * findById/EntityManager.find(), por eso se usa este método para cualquier
     * lookup de un Holiday específico.
     */
    Optional<Holiday> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Todos los feriados de la empresa (sin filtro de año). */
    List<Holiday> findByCompanyId(UUID companyId);

    /** Feriados de la empresa dentro de un rango de fechas (para ?anio=). */
    List<Holiday> findByCompanyIdAndFechaBetween(UUID companyId, LocalDate desde, LocalDate hasta);

    // ── métodos de deduplicación (branch_id IS NULL = feriado global) ──────────

    boolean existsByCompanyIdAndBranchIdIsNullAndFecha(UUID companyId, LocalDate fecha);

    boolean existsByCompanyIdAndBranchIdAndFecha(UUID companyId, UUID branchId, LocalDate fecha);

    /** Self-exclusion en PATCH: excluye al propio feriado de la búsqueda de duplicados. */
    boolean existsByCompanyIdAndBranchIdIsNullAndFechaAndIdNot(
            UUID companyId, LocalDate fecha, UUID excludeId);

    boolean existsByCompanyIdAndBranchIdAndFechaAndIdNot(
            UUID companyId, UUID branchId, LocalDate fecha, UUID excludeId);
}
```

- [ ] **Step 6: Verificar que el test sigue rojo (controller aún no existe)**

```bash
cd backend && ./mvnw test -pl . -Dtest=HolidayControllerTest#redVerification_adminCreatesHoliday -q
```

Resultado esperado: **FAIL** (igual que antes). Si compiló y el test pasa de alguna forma, revisar que no hay un controller viejo.

- [ ] **Step 7: Commit**

```bash
cd backend
git add src/main/resources/db/migration/V7__create_holiday.sql \
        src/main/java/com/staffly/backend/holiday/Holiday.java \
        src/main/java/com/staffly/backend/holiday/HolidayRepository.java \
        src/test/java/com/staffly/backend/holiday/HolidayControllerTest.java
git commit -m "feat: agregar modelo y persistencia de feriados"
```

---

## Task 2: DTOs + servicio + controller (TDD)

**Files:**
- Create: `backend/src/main/java/com/staffly/backend/holiday/dto/HolidayResponse.java`
- Create: `backend/src/main/java/com/staffly/backend/holiday/dto/CreateHolidayRequest.java`
- Create: `backend/src/main/java/com/staffly/backend/holiday/dto/UpdateHolidayRequest.java`
- Create: `backend/src/main/java/com/staffly/backend/holiday/HolidayService.java`
- Create: `backend/src/main/java/com/staffly/backend/holiday/HolidayController.java`
- Modify: `backend/src/test/java/com/staffly/backend/holiday/HolidayControllerTest.java` (reemplazar con suite completa)

**Interfaces:**
- Consumes (de Task 1): `Holiday`, `HolidayRepository` (todos los métodos), `BranchRepository.findByIdAndCompanyId`
- Produces: `GET/POST/PATCH/DELETE /api/v1/holidays` según spec

---

- [ ] **Step 1: Reemplazar el test RED con la suite completa**

Reemplazar el contenido de `HolidayControllerTest.java` con la suite completa (mantener mismos imports + helpers, agregar todos los tests):

```java
// backend/src/test/java/com/staffly/backend/holiday/HolidayControllerTest.java
package com.staffly.backend.holiday;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HolidayControllerTest {

    private static final String PASSWORD = "Password123";
    private static final String BASE_URL = "/api/v1/holidays";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID companyAId;
    private UUID companyBId;
    private Branch branchA1;
    private Branch branchA2;
    private String adminAToken;

    @BeforeEach
    void seedTwoCompanies() throws Exception {
        companyAId = createCompany("Empresa A");
        branchA1 = createBranch(companyAId, "Sucursal A1");
        branchA2 = createBranch(companyAId, "Sucursal A2");
        adminAToken = createUserAndLogin(companyAId, "admin-a@empresa-a.com", RolUsuario.ADMIN, null);
        companyBId = createCompany("Empresa B");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private Branch createBranch(UUID companyId, String nombre) {
        Branch branch = new Branch();
        branch.setCompanyId(companyId);
        branch.setNombre(nombre);
        branch.setDireccion("Dirección");
        branch.setZonaHoraria("America/Argentina/Buenos_Aires");
        branch.setEstado(EstadoSucursal.ACTIVA);
        entityManager.persist(branch);
        entityManager.flush();
        return branch;
    }

    private String createUserAndLogin(UUID companyId, String email, RolUsuario rol, Branch assignedBranch)
            throws Exception {
        User user = new User();
        user.setCompanyId(companyId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRol(rol);
        user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(false);
        if (assignedBranch != null) user.getBranches().add(assignedBranch);
        userRepository.save(user);
        entityManager.flush();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }

    private String createHoliday(String token, String fecha, String nombre, UUID branchId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fecha", fecha);
        body.put("nombre", nombre);
        body.put("recurrente", false);
        if (branchId != null) body.put("branchId", branchId.toString());

        String response = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void adminCrudLifecycle() throws Exception {
        // crear y verificar respuesta
        String holidayId = createHoliday(adminAToken, "2026-12-25", "Navidad", null);

        // listar — orden por fecha ASC
        createHoliday(adminAToken, "2026-01-01", "Año Nuevo", null);
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fecha").value("2026-01-01"))
                .andExpect(jsonPath("$[1].fecha").value("2026-12-25"));

        // PATCH parcial: solo nombre y recurrente
        mockMvc.perform(patch(BASE_URL + "/" + holidayId)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Navidad Actualizada", "recurrente", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Navidad Actualizada"))
                .andExpect(jsonPath("$.recurrente").value(true))
                .andExpect(jsonPath("$.fecha").value("2026-12-25"));

        // DELETE
        mockMvc.perform(delete(BASE_URL + "/" + holidayId)
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void adminCreatesGlobalAndBranchSpecificHoliday() throws Exception {
        // global (sin branchId) — branchId viene null en el JSON
        String globalId = createHoliday(adminAToken, "2026-12-25", "Navidad", null);
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchId").value(org.hamcrest.Matchers.nullValue()));

        // específico de sucursal
        String branchSpecificId = createHoliday(adminAToken, "2026-07-09", "Día de la Independencia", branchA1.getId());
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].branchId").value(branchA1.getId().toString()));
    }

    @Test
    void rrhhCanListButNotWrite() throws Exception {
        String rrhhToken = createUserAndLogin(companyAId, "rrhh@empresa-a.com", RolUsuario.RRHH, null);
        createHoliday(adminAToken, "2026-12-25", "Navidad", null);

        // RRHH puede listar
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + rrhhToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // RRHH no puede crear
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + rrhhToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-01-01", "nombre", "Año Nuevo", "recurrente", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorScopedVisibility() throws Exception {
        String supervisorToken = createUserAndLogin(companyAId, "supervisor@empresa-a.com",
                RolUsuario.SUPERVISOR, branchA1);

        // admin crea: 1 global, 1 para branchA1, 1 para branchA2
        createHoliday(adminAToken, "2026-12-25", "Navidad", null);
        createHoliday(adminAToken, "2026-07-09", "Feriado A1", branchA1.getId());
        createHoliday(adminAToken, "2026-05-01", "Feriado A2", branchA2.getId());

        // supervisor ve global + branchA1, no ve branchA2
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // supervisor filtra por ?branchId= de su sucursal → global + branchA1
        mockMvc.perform(get(BASE_URL + "?branchId=" + branchA1.getId())
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void supervisorCannotFilterByOutOfScopeBranch() throws Exception {
        String supervisorToken = createUserAndLogin(companyAId, "supervisor@empresa-a.com",
                RolUsuario.SUPERVISOR, branchA1);

        // branchA2 no está en el alcance del supervisor
        mockMvc.perform(get(BASE_URL + "?branchId=" + branchA2.getId())
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeHasNoAccess() throws Exception {
        // Crear un usuario EMPLOYEE sin employee vinculado
        String employeeToken = createUserAndLogin(companyAId, "empleado@empresa-a.com",
                RolUsuario.EMPLOYEE, null);

        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25", "nombre", "Navidad", "recurrente", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateValidations() throws Exception {
        // Duplicado global mismo día → 409
        createHoliday(adminAToken, "2026-12-25", "Navidad", null);
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25", "nombre", "Navidad 2", "recurrente", false))))
                .andExpect(status().isConflict());

        // Duplicado mismo branch mismo día → 409
        createHoliday(adminAToken, "2026-07-09", "Feriado A1", branchA1.getId());
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-07-09", "nombre", "Feriado A1 bis",
                                "recurrente", false, "branchId", branchA1.getId().toString()))))
                .andExpect(status().isConflict());

        // Global + específico mismo día → 201 (ámbitos distintos)
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25", "nombre", "Feriado branchA1 en Navidad",
                                "recurrente", false, "branchId", branchA1.getId().toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void branchIdNotFoundReturns404() throws Exception {
        UUID unknownBranchId = UUID.randomUUID();
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-12-25", "nombre", "Navidad",
                                "recurrente", false, "branchId", unknownBranchId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantIsolation() throws Exception {
        // holiday de empresa B: admin de A no debe verlo (lo crea directo en la BD)
        Branch branchB1 = createBranch(companyBId, "Sucursal B1");
        Holiday holidayB = new Holiday();
        holidayB.setCompanyId(companyBId);
        holidayB.setFecha(java.time.LocalDate.of(2026, 12, 25));
        holidayB.setNombre("Navidad B");
        holidayB.setRecurrente(false);
        entityManager.persist(holidayB);
        entityManager.flush();

        // admin A no ve el holiday de empresa B en el listado
        mockMvc.perform(get(BASE_URL).header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // PATCH sobre el holiday de empresa B → 404
        mockMvc.perform(patch(BASE_URL + "/" + holidayB.getId())
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Intento"))))
                .andExpect(status().isNotFound());

        // branchId de empresa B en create para empresa A → 404
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fecha", "2026-07-09", "nombre", "Feriado",
                                "recurrente", false, "branchId", branchB1.getId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anioFilterWorksCorrectly() throws Exception {
        createHoliday(adminAToken, "2025-12-25", "Navidad 2025", null);
        createHoliday(adminAToken, "2026-07-09", "Independencia 2026", null);

        // solo muestra los de 2026
        mockMvc.perform(get(BASE_URL + "?anio=2026").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fecha").value("2026-07-09"));

        // solo muestra los de 2025
        mockMvc.perform(get(BASE_URL + "?anio=2025").header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fecha").value("2025-12-25"));
    }

    @Test
    void patchSelfExclusionNoDuplicateError() throws Exception {
        // Crear un feriado
        String holidayId = createHoliday(adminAToken, "2026-12-25", "Navidad", null);

        // PATCH con la misma fecha → NO debe 409 (se excluye a sí mismo)
        mockMvc.perform(patch(BASE_URL + "/" + holidayId)
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("nombre", "Navidad Actualizada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Navidad Actualizada"));
    }
}
```

- [ ] **Step 2: Ejecutar suite completa para confirmar RED**

```bash
cd backend && ./mvnw test -pl . -Dtest=HolidayControllerTest -q
```

Resultado esperado: **todos los tests FAIL** — `Expected: 2xx, was: 4xx o 5xx` (controller no existe aún). Si algún test pasa por accidente, revisar antes de continuar.

- [ ] **Step 3: Escribir `HolidayResponse`**

```java
// backend/src/main/java/com/staffly/backend/holiday/dto/HolidayResponse.java
package com.staffly.backend.holiday.dto;

import com.staffly.backend.holiday.Holiday;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(UUID id, UUID branchId, LocalDate fecha, String nombre, boolean recurrente) {

    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(
                holiday.getId(),
                holiday.getBranchId(),
                holiday.getFecha(),
                holiday.getNombre(),
                holiday.isRecurrente());
    }
}
```

- [ ] **Step 4: Escribir `CreateHolidayRequest`**

```java
// backend/src/main/java/com/staffly/backend/holiday/dto/CreateHolidayRequest.java
package com.staffly.backend.holiday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateHolidayRequest(
        UUID branchId,
        @NotNull LocalDate fecha,
        @NotBlank String nombre,
        boolean recurrente) {
}
```

- [ ] **Step 5: Escribir `UpdateHolidayRequest`**

```java
// backend/src/main/java/com/staffly/backend/holiday/dto/UpdateHolidayRequest.java
package com.staffly.backend.holiday.dto;

import java.time.LocalDate;
import java.util.UUID;

// Actualización parcial: null = no cambiar.
// recurrente es Boolean boxeado para distinguir null (no cambiar) de false (poner en false).
// branchId: null = no cambiar. No se puede convertir un feriado de branch-específico
// a global vía PATCH — delete + recreate en ese caso.
public record UpdateHolidayRequest(UUID branchId, LocalDate fecha, String nombre, Boolean recurrente) {
}
```

- [ ] **Step 6: Escribir `HolidayService`**

```java
// backend/src/main/java/com/staffly/backend/holiday/HolidayService.java
package com.staffly.backend.holiday;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.holiday.dto.CreateHolidayRequest;
import com.staffly.backend.holiday.dto.HolidayResponse;
import com.staffly.backend.holiday.dto.UpdateHolidayRequest;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final BranchRepository branchRepository;

    public HolidayService(HolidayRepository holidayRepository, BranchRepository branchRepository) {
        this.holidayRepository = holidayRepository;
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> list(UUID branchId, Integer anio, StafflyUserPrincipal principal) {
        // SUPERVISOR: ?branchId= de una sucursal fuera de su alcance → 404
        if (principal.getRol() == Rol.SUPERVISOR
                && branchId != null
                && !principal.getBranchIds().contains(branchId)) {
            throw new ResourceNotFoundException("No se encontró la sucursal solicitada");
        }

        UUID companyId = principal.getCompanyId();
        List<Holiday> holidays;
        if (anio != null) {
            holidays = holidayRepository.findByCompanyIdAndFechaBetween(
                    companyId, LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31));
        } else {
            holidays = holidayRepository.findByCompanyId(companyId);
        }

        return holidays.stream()
                .filter(h -> isVisibleFor(h, branchId, principal))
                .sorted(Comparator.comparing(Holiday::getFecha))
                .map(HolidayResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public HolidayResponse create(CreateHolidayRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        Branch branch = resolveBranch(request.branchId(), companyId);
        validarDuplicado(companyId, request.branchId(), request.fecha(), null);

        Holiday holiday = new Holiday();
        holiday.setCompanyId(companyId);
        holiday.setBranch(branch);
        holiday.setFecha(request.fecha());
        holiday.setNombre(request.nombre().strip());
        holiday.setRecurrente(request.recurrente());
        return HolidayResponse.from(holidayRepository.save(holiday));
    }

    @Transactional
    public HolidayResponse update(UUID id, UpdateHolidayRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el feriado solicitado"));

        // estado final: valor entrante o el ya guardado
        UUID branchIdFinal = request.branchId() != null ? request.branchId() : holiday.getBranchId();
        LocalDate fechaFinal = request.fecha() != null ? request.fecha() : holiday.getFecha();
        String nombreFinal = request.nombre() != null ? request.nombre().strip() : holiday.getNombre();
        boolean recurrenteFinal = request.recurrente() != null ? request.recurrente() : holiday.isRecurrente();

        if (nombreFinal.isBlank()) {
            throw new BadRequestException("El nombre no puede estar vacío");
        }

        Branch branch = resolveBranch(branchIdFinal, companyId);
        validarDuplicado(companyId, branchIdFinal, fechaFinal, holiday.getId());

        holiday.setBranch(branch);
        holiday.setFecha(fechaFinal);
        holiday.setNombre(nombreFinal);
        holiday.setRecurrente(recurrenteFinal);
        return HolidayResponse.from(holidayRepository.save(holiday));
    }

    @Transactional
    public void delete(UUID id, StafflyUserPrincipal principal) {
        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el feriado solicitado"));
        holidayRepository.delete(holiday);
    }

    // ── helpers privados ──────────────────────────────────────────────────────

    /**
     * Devuelve el Branch si branchId no es null; verifica que pertenezca a la
     * empresa del JWT. branchId null = feriado global → devuelve null.
     */
    private Branch resolveBranch(UUID branchId, UUID companyId) {
        if (branchId == null) return null;
        return branchRepository.findByIdAndCompanyId(branchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada"));
    }

    /**
     * Verifica que no exista otro feriado con la misma (company_id, branch_id,
     * fecha). excludeId se usa en PATCH para que el propio feriado no se cuente
     * como duplicado de sí mismo. Las dos ramas del if son necesarias porque
     * Spring Data JPA no genera IS NULL cuando se pasa null directamente a un
     * parámetro de query (necesita método distinto con IsNull en el nombre).
     */
    private void validarDuplicado(UUID companyId, UUID branchId, LocalDate fecha, UUID excludeId) {
        boolean existe;
        if (branchId == null) {
            existe = excludeId == null
                    ? holidayRepository.existsByCompanyIdAndBranchIdIsNullAndFecha(companyId, fecha)
                    : holidayRepository.existsByCompanyIdAndBranchIdIsNullAndFechaAndIdNot(companyId, fecha, excludeId);
        } else {
            existe = excludeId == null
                    ? holidayRepository.existsByCompanyIdAndBranchIdAndFecha(companyId, branchId, fecha)
                    : holidayRepository.existsByCompanyIdAndBranchIdAndFechaAndIdNot(companyId, branchId, fecha, excludeId);
        }
        if (existe) {
            throw new ConflictException("Ya existe un feriado para esa fecha en ese ámbito");
        }
    }

    /**
     * Determina si un holiday es visible para el rol/filtro dados.
     * Reglas:
     * - ?branchId= X: solo globales + feriados de esa sucursal
     * - SUPERVISOR: solo globales + feriados de sus sucursales asignadas
     */
    private boolean isVisibleFor(Holiday holiday, UUID branchIdFilter, StafflyUserPrincipal principal) {
        UUID hBranchId = holiday.getBranchId();

        if (branchIdFilter != null && hBranchId != null && !hBranchId.equals(branchIdFilter)) {
            return false;
        }

        if (principal.getRol() == Rol.SUPERVISOR
                && hBranchId != null
                && !principal.getBranchIds().contains(hBranchId)) {
            return false;
        }

        return true;
    }
}
```

- [ ] **Step 7: Escribir `HolidayController`**

```java
// backend/src/main/java/com/staffly/backend/holiday/HolidayController.java
package com.staffly.backend.holiday;

import com.staffly.backend.holiday.dto.CreateHolidayRequest;
import com.staffly.backend.holiday.dto.HolidayResponse;
import com.staffly.backend.holiday.dto.UpdateHolidayRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR')")
    public ResponseEntity<List<HolidayResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer anio,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(holidayService.list(branchId, anio, principal));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HolidayResponse> create(
            @Valid @RequestBody CreateHolidayRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        HolidayResponse response = holidayService.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/holidays/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HolidayResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHolidayRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(holidayService.update(id, request, principal));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        holidayService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 8: Ejecutar suite completa y verificar GREEN**

```bash
cd backend && ./mvnw test -pl . -Dtest=HolidayControllerTest -q
```

Resultado esperado: **todos los tests PASS**. Si alguno falla, leer el mensaje de error antes de corregir.

- [ ] **Step 9: Ejecutar suite completa del proyecto para verificar sin regresiones**

```bash
cd backend && ./mvnw test -q
```

Resultado esperado: **BUILD SUCCESS**, suite completa en verde (92+ tests, sin regresiones en los módulos anteriores).

- [ ] **Step 10: Commit**

```bash
cd backend
git add src/main/java/com/staffly/backend/holiday/ \
        src/test/java/com/staffly/backend/holiday/HolidayControllerTest.java
git commit -m "feat: agregar crud de feriados con validaciones y scoping por rol"
```
