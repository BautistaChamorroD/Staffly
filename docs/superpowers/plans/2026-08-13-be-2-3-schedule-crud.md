# BE-2.3 — Schedule CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar el módulo `schedule/` con CRUD completo, validación de solapamiento (409 duro), advertencia de fuera-de-disponibilidad (201 + warning + audit), cambios de estado, y extracción de `EmployeeResolver` como componente compartido.

**Architecture:** `EmployeeResolver` en `employee/` centraliza el scoping de acceso al Employee (tenant + SUPERVISOR + EMPLOYEE). `ScheduleService` implementa la lógica de negocio: overlap contra todos los turnos del empleado en la empresa vía JPQL, y disponibilidad con aritmética en minutos con wrap por segmento de día. `ScheduleController` expone 7 endpoints bajo `/api/v1/schedules`.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, Hibernate 6.6, H2 (test), Flyway, JUnit 5 + MockMvc.

## Global Constraints

- `company_id` siempre desde JWT (`principal.getCompanyId()`), nunca del cliente
- Toda lookup de recurso único usa `findByIdAndCompanyId` (el `@Filter tenantFilter` no cubre `findById`)
- SUPERVISOR: accede a recursos de sus sucursales asignadas; recurso fuera de scope → 404
- EMPLOYEE: solo lectura de sus propios schedules; write → 403 (manejado por `@PreAuthorize`)
- Cross-tenant: recurso de otra empresa → 404, nunca 403
- Solapamiento: validar contra TODOS los turnos del empleado en la empresa (cualquier sucursal) → 409
- `fechaHoraFin` debe ser posterior a `fechaHoraInicio` → 400 si no se cumple
- Estado `DELETE`: solo `PLANIFICADO` → 409 si otro estado
- Estado `confirm`: solo `PLANIFICADO` → `CONFIRMADO` → 409 si otro estado de entrada
- Estado `status`: solo `CONFIRMADO` → `CUMPLIDO`|`AUSENTE` → 409 si otro estado de entrada
- `horas_totales` se computa en el DTO, no se almacena
- Disponibilidad: sin cobertura = `OUT_OF_AVAILABILITY` (no se asume disponibilidad total)
- Conventional commits en español: `feat:`, `fix:`, `refactor:`, etc.
- No ON DELETE CASCADE en V9 (consistente con V6, V7)
- Tests usan `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`, entityManager.flush() después de cada persist

---

## File Map

**Crear:**
- `backend/src/main/java/com/staffly/backend/employee/EmployeeResolver.java`
- `backend/src/main/resources/db/migration/V9__create_schedule.sql`
- `backend/src/main/java/com/staffly/backend/schedule/TipoTurno.java`
- `backend/src/main/java/com/staffly/backend/schedule/EstadoTurno.java`
- `backend/src/main/java/com/staffly/backend/schedule/Schedule.java`
- `backend/src/main/java/com/staffly/backend/schedule/ScheduleRepository.java`
- `backend/src/main/java/com/staffly/backend/schedule/dto/CreateScheduleRequest.java`
- `backend/src/main/java/com/staffly/backend/schedule/dto/UpdateScheduleRequest.java`
- `backend/src/main/java/com/staffly/backend/schedule/dto/UpdateStatusRequest.java`
- `backend/src/main/java/com/staffly/backend/schedule/dto/ScheduleResponse.java`
- `backend/src/main/java/com/staffly/backend/schedule/ScheduleService.java`
- `backend/src/main/java/com/staffly/backend/schedule/ScheduleController.java`
- `backend/src/test/java/com/staffly/backend/schedule/ScheduleControllerTest.java`

**Modificar:**
- `backend/src/main/java/com/staffly/backend/availability/AvailabilityService.java` — usar EmployeeResolver
- `backend/src/main/java/com/staffly/backend/availability/DiaSemana.java` — agregar `fromDayOfWeek`

---

### Task 1: EmployeeResolver + refactor AvailabilityService

**Files:**
- Create: `backend/src/main/java/com/staffly/backend/employee/EmployeeResolver.java`
- Modify: `backend/src/main/java/com/staffly/backend/availability/AvailabilityService.java`
- Verify: `backend/src/test/java/com/staffly/backend/availability/AvailabilityControllerTest.java` (no nuevos tests — la suite existente debe seguir pasando)

**Interfaces:**
- Produce: `EmployeeResolver.resolveForCaller(UUID employeeId, StafflyUserPrincipal principal, boolean allowEmployee): Employee`

- [ ] **Step 1: Verificar baseline — correr la suite de availability**

```bash
cd backend && ./mvnw test -pl . -Dtest=AvailabilityControllerTest -q
```
Expected: todos los tests PASS. Si alguno falla, investigar antes de continuar.

- [ ] **Step 2: Crear EmployeeResolver**

```java
// backend/src/main/java/com/staffly/backend/employee/EmployeeResolver.java
package com.staffly.backend.employee;

import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Componente compartido que resuelve el Employee que el caller puede tocar,
 * aplicando las tres capas de scoping: tenant (404), rol EMPLOYEE (403/self-only),
 * SUPERVISOR (404 fuera de sus sucursales). Evita duplicación entre
 * AvailabilityService, ScheduleService, y futuros servicios.
 */
@Component
public class EmployeeResolver {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public EmployeeResolver(EmployeeRepository employeeRepository, UserRepository userRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    /**
     * @param allowEmployee true = EMPLOYEE puede acceder a su propio registro (403 si intenta ver otro).
     *                      false = EMPLOYEE no tiene acceso (403 directo).
     */
    public Employee resolveForCaller(UUID employeeId, StafflyUserPrincipal principal, boolean allowEmployee) {
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado solicitado"));

        if (principal.getRol() == Rol.EMPLOYEE) {
            if (!allowEmployee) {
                throw new AccessDeniedException("No tenés permisos para esta operación");
            }
            UUID ownEmployeeId = userRepository.findByIdAndCompanyId(principal.getUserId(), principal.getCompanyId())
                    .map(User::getEmployee)
                    .map(Employee::getId)
                    .orElse(null);
            if (!employee.getId().equals(ownEmployeeId)) {
                throw new AccessDeniedException("Solo podés acceder a tu propio registro");
            }
        }

        if (principal.getRol() == Rol.SUPERVISOR
                && employee.getBranches().stream().noneMatch(b -> principal.getBranchIds().contains(b.getId()))) {
            throw new ResourceNotFoundException("No se encontró el empleado solicitado");
        }

        return employee;
    }
}
```

- [ ] **Step 3: Refactorizar AvailabilityService para usar EmployeeResolver**

Reemplazar la clase completa. Los cambios clave: (1) el constructor pierde `EmployeeRepository` y `UserRepository`, gana `EmployeeResolver`; (2) el método privado `resolveEmployee` se elimina; (3) las 4 llamadas a `resolveEmployee(employeeId, principal)` se reemplazan con `employeeResolver.resolveForCaller(employeeId, principal, true)`.

```java
// backend/src/main/java/com/staffly/backend/availability/AvailabilityService.java
package com.staffly.backend.availability;

import com.staffly.backend.availability.dto.AvailabilityResponse;
import com.staffly.backend.availability.dto.CreateAvailabilityRequest;
import com.staffly.backend.availability.dto.UpdateAvailabilityRequest;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeResolver;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final EmployeeResolver employeeResolver;

    public AvailabilityService(AvailabilityRepository availabilityRepository, EmployeeResolver employeeResolver) {
        this.availabilityRepository = availabilityRepository;
        this.employeeResolver = employeeResolver;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> list(UUID employeeId, StafflyUserPrincipal principal) {
        Employee employee = employeeResolver.resolveForCaller(employeeId, principal, true);
        return availabilityRepository.findByCompanyIdAndEmployeeId(principal.getCompanyId(), employee.getId()).stream()
                .sorted(Comparator
                        .comparing((EmployeeAvailability a) -> a.getDiaSemana().ordinal())
                        .thenComparing(EmployeeAvailability::getHoraInicio))
                .map(AvailabilityResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AvailabilityResponse create(UUID employeeId, CreateAvailabilityRequest request, StafflyUserPrincipal principal) {
        Employee employee = employeeResolver.resolveForCaller(employeeId, principal, true);
        LocalTime horaInicio = request.horaInicio().truncatedTo(ChronoUnit.MINUTES);
        LocalTime horaFin = request.horaFin().truncatedTo(ChronoUnit.MINUTES);
        validarFranja(horaInicio, horaFin);
        validarSolape(employee, request.diaSemana(), horaInicio, horaFin, null, principal);

        EmployeeAvailability availability = new EmployeeAvailability();
        availability.setCompanyId(principal.getCompanyId());
        availability.setEmployee(employee);
        availability.setDiaSemana(request.diaSemana());
        availability.setHoraInicio(horaInicio);
        availability.setHoraFin(horaFin);
        return AvailabilityResponse.from(availabilityRepository.save(availability));
    }

    @Transactional
    public AvailabilityResponse update(
            UUID employeeId, UUID id, UpdateAvailabilityRequest request, StafflyUserPrincipal principal) {
        Employee employee = employeeResolver.resolveForCaller(employeeId, principal, true);
        EmployeeAvailability availability = findFranjaOrThrow(id, employee, principal);

        DiaSemana diaFinal = request.diaSemana() != null ? request.diaSemana() : availability.getDiaSemana();
        LocalTime inicioFinal = request.horaInicio() != null ? request.horaInicio() : availability.getHoraInicio();
        LocalTime finFinal = request.horaFin() != null ? request.horaFin() : availability.getHoraFin();

        inicioFinal = inicioFinal.truncatedTo(ChronoUnit.MINUTES);
        finFinal = finFinal.truncatedTo(ChronoUnit.MINUTES);

        validarFranja(inicioFinal, finFinal);
        validarSolape(employee, diaFinal, inicioFinal, finFinal, availability.getId(), principal);

        availability.setDiaSemana(diaFinal);
        availability.setHoraInicio(inicioFinal);
        availability.setHoraFin(finFinal);
        return AvailabilityResponse.from(availabilityRepository.save(availability));
    }

    @Transactional
    public void delete(UUID employeeId, UUID id, StafflyUserPrincipal principal) {
        Employee employee = employeeResolver.resolveForCaller(employeeId, principal, true);
        EmployeeAvailability availability = findFranjaOrThrow(id, employee, principal);
        availabilityRepository.delete(availability);
    }

    private EmployeeAvailability findFranjaOrThrow(UUID id, Employee employee, StafflyUserPrincipal principal) {
        EmployeeAvailability availability = availabilityRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la franja solicitada"));
        if (!availability.getEmployee().getId().equals(employee.getId())) {
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
            if (existente.getId().equals(excludeId)) continue;
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

    private int finEnMinutos(LocalTime inicio, LocalTime fin) {
        int minutosFin = enMinutos(fin);
        return minutosFin <= enMinutos(inicio) ? minutosFin + 24 * 60 : minutosFin;
    }
}
```

- [ ] **Step 4: Correr la suite de availability para verificar que nada se rompió**

```bash
cd backend && ./mvnw test -pl . -Dtest=AvailabilityControllerTest -q
```
Expected: todos los tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/staffly/backend/employee/EmployeeResolver.java \
        backend/src/main/java/com/staffly/backend/availability/AvailabilityService.java
git commit -m "refactor: extraer EmployeeResolver compartido y simplificar AvailabilityService"
```

---

### Task 2: V9 + enums + entidad + repositorio + DTOs

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__create_schedule.sql`
- Modify: `backend/src/main/java/com/staffly/backend/availability/DiaSemana.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/TipoTurno.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/EstadoTurno.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/Schedule.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/ScheduleRepository.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/dto/CreateScheduleRequest.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/dto/UpdateScheduleRequest.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/dto/UpdateStatusRequest.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/dto/ScheduleResponse.java`

**Interfaces:**
- Consume: `EmployeeResolver` (Task 1)
- Produce: `ScheduleRepository.existsOverlap(UUID, UUID, LocalDateTime, LocalDateTime, UUID): boolean`
- Produce: `ScheduleResponse.from(Schedule): ScheduleResponse` y `ScheduleResponse.from(Schedule, String): ScheduleResponse`
- Produce: `DiaSemana.fromDayOfWeek(DayOfWeek): DiaSemana`

- [ ] **Step 1: Crear migración V9**

```sql
-- backend/src/main/resources/db/migration/V9__create_schedule.sql
CREATE TABLE schedule (
    id UUID NOT NULL PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    employee_id UUID NOT NULL REFERENCES employee (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    fecha_hora_inicio TIMESTAMP NOT NULL,
    fecha_hora_fin TIMESTAMP NOT NULL,
    tipo_turno VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PLANIFICADO'
);

CREATE INDEX idx_schedule_company_employee ON schedule(company_id, employee_id);
```

- [ ] **Step 2: Agregar fromDayOfWeek a DiaSemana**

```java
// backend/src/main/java/com/staffly/backend/availability/DiaSemana.java
package com.staffly.backend.availability;

import java.time.DayOfWeek;

public enum DiaSemana {
    LUNES,
    MARTES,
    MIERCOLES,
    JUEVES,
    VIERNES,
    SABADO,
    DOMINGO;

    public static DiaSemana fromDayOfWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> LUNES;
            case TUESDAY -> MARTES;
            case WEDNESDAY -> MIERCOLES;
            case THURSDAY -> JUEVES;
            case FRIDAY -> VIERNES;
            case SATURDAY -> SABADO;
            case SUNDAY -> DOMINGO;
        };
    }
}
```

- [ ] **Step 3: Crear TipoTurno**

```java
// backend/src/main/java/com/staffly/backend/schedule/TipoTurno.java
package com.staffly.backend.schedule;

public enum TipoTurno {
    FIJO,
    ROTATIVO
}
```

- [ ] **Step 4: Crear EstadoTurno**

```java
// backend/src/main/java/com/staffly/backend/schedule/EstadoTurno.java
package com.staffly.backend.schedule;

public enum EstadoTurno {
    PLANIFICADO,
    CONFIRMADO,
    CUMPLIDO,
    AUSENTE
}
```

- [ ] **Step 5: Crear entidad Schedule**

```java
// backend/src/main/java/com/staffly/backend/schedule/Schedule.java
package com.staffly.backend.schedule;

import com.staffly.backend.branch.Branch;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Schedule extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private LocalDateTime fechaHoraInicio;

    @Column(name = "fecha_hora_fin", nullable = false)
    private LocalDateTime fechaHoraFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_turno", nullable = false)
    private TipoTurno tipoTurno;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoTurno estado = EstadoTurno.PLANIFICADO;

    public UUID getId() { return id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public LocalDateTime getFechaHoraInicio() { return fechaHoraInicio; }
    public void setFechaHoraInicio(LocalDateTime fechaHoraInicio) { this.fechaHoraInicio = fechaHoraInicio; }

    public LocalDateTime getFechaHoraFin() { return fechaHoraFin; }
    public void setFechaHoraFin(LocalDateTime fechaHoraFin) { this.fechaHoraFin = fechaHoraFin; }

    public TipoTurno getTipoTurno() { return tipoTurno; }
    public void setTipoTurno(TipoTurno tipoTurno) { this.tipoTurno = tipoTurno; }

    public EstadoTurno getEstado() { return estado; }
    public void setEstado(EstadoTurno estado) { this.estado = estado; }
}
```

- [ ] **Step 6: Crear ScheduleRepository**

```java
// backend/src/main/java/com/staffly/backend/schedule/ScheduleRepository.java
package com.staffly.backend.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    Optional<Schedule> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Schedule> findByCompanyId(UUID companyId);

    /**
     * Detecta solapamiento: cualquier turno existente del empleado cuyo intervalo
     * se interseque con [inicio, fin). excludeId se usa en PATCH para excluir el
     * turno que se está editando; pasar null en POST.
     */
    @Query("""
        SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
        FROM Schedule s
        WHERE s.companyId = :companyId
          AND s.employee.id = :employeeId
          AND s.fechaHoraInicio < :fin
          AND s.fechaHoraFin > :inicio
          AND (:excludeId IS NULL OR s.id <> :excludeId)
    """)
    boolean existsOverlap(
            @Param("companyId") UUID companyId,
            @Param("employeeId") UUID employeeId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("excludeId") UUID excludeId);
}
```

- [ ] **Step 7: Crear DTOs**

```java
// backend/src/main/java/com/staffly/backend/schedule/dto/CreateScheduleRequest.java
package com.staffly.backend.schedule.dto;

import com.staffly.backend.schedule.TipoTurno;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateScheduleRequest(
        @NotNull UUID employeeId,
        @NotNull UUID branchId,
        @NotNull LocalDateTime fechaHoraInicio,
        @NotNull LocalDateTime fechaHoraFin,
        @NotNull TipoTurno tipoTurno
) {}
```

```java
// backend/src/main/java/com/staffly/backend/schedule/dto/UpdateScheduleRequest.java
package com.staffly.backend.schedule.dto;

import com.staffly.backend.schedule.TipoTurno;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateScheduleRequest(
        UUID branchId,
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        TipoTurno tipoTurno
) {}
```

```java
// backend/src/main/java/com/staffly/backend/schedule/dto/UpdateStatusRequest.java
package com.staffly.backend.schedule.dto;

import com.staffly.backend.schedule.EstadoTurno;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull EstadoTurno estado) {}
```

```java
// backend/src/main/java/com/staffly/backend/schedule/dto/ScheduleResponse.java
package com.staffly.backend.schedule.dto;

import com.staffly.backend.schedule.EstadoTurno;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.TipoTurno;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        UUID employeeId,
        UUID branchId,
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        TipoTurno tipoTurno,
        EstadoTurno estado,
        double horasTotales,
        String warning
) {
    public static ScheduleResponse from(Schedule s) {
        return from(s, null);
    }

    public static ScheduleResponse from(Schedule s, String warning) {
        double horas = Duration.between(s.getFechaHoraInicio(), s.getFechaHoraFin()).toMinutes() / 60.0;
        return new ScheduleResponse(
                s.getId(),
                s.getEmployee().getId(),
                s.getBranch().getId(),
                s.getFechaHoraInicio(),
                s.getFechaHoraFin(),
                s.getTipoTurno(),
                s.getEstado(),
                horas,
                warning);
    }
}
```

- [ ] **Step 8: Verificar que la aplicación arranca con V9**

```bash
cd backend && ./mvnw test -pl . -Dtest=AvailabilityControllerTest -q
```
Expected: PASS (verifica que el contexto arranca con V9 y que el refactor de Task 1 sigue funcionando).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/db/migration/V9__create_schedule.sql \
        backend/src/main/java/com/staffly/backend/availability/DiaSemana.java \
        backend/src/main/java/com/staffly/backend/schedule/
git commit -m "feat: agregar migración V9, entidad Schedule y DTOs"
```

---

### Task 3: ScheduleService + ScheduleController + suite de tests

**Files:**
- Create: `backend/src/main/java/com/staffly/backend/schedule/ScheduleService.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/ScheduleController.java`
- Create: `backend/src/test/java/com/staffly/backend/schedule/ScheduleControllerTest.java`

**Interfaces:**
- Consume (Task 1): `EmployeeResolver.resolveForCaller`
- Consume (Task 2): `Schedule`, `ScheduleRepository.existsOverlap`, `ScheduleResponse.from`, `DiaSemana.fromDayOfWeek`, `EstadoTurno`, `TipoTurno`
- Consume (BE-2.1): `AvailabilityRepository.findByCompanyIdAndEmployeeIdAndDiaSemana`
- Consume (BE-1.8): `AuditableFieldChangedEvent`, `ApplicationEventPublisher`
- Consume (existing): `BranchRepository.findByIdAndCompanyId`, `UserRepository.findByIdAndCompanyId`

- [ ] **Step 1: Escribir el test class completo (RED)**

```java
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
    private String empToken2;       // linked to emp2

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
        empToken2     = createUserAndLogin(companyAId, "emp2@a.com",       RolUsuario.EMPLOYEE,   emp2,  null);

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
```

- [ ] **Step 2: Correr los tests para verificar que fallan (RED)**

```bash
cd backend && ./mvnw test -pl . -Dtest=ScheduleControllerTest -q 2>&1 | tail -5
```
Expected: ERROR (no se puede crear el contexto porque faltan ScheduleService y ScheduleController) o compilation error.

- [ ] **Step 3: Implementar ScheduleService**

```java
// backend/src/main/java/com/staffly/backend/schedule/ScheduleService.java
package com.staffly.backend.schedule;

import com.staffly.backend.availability.AvailabilityRepository;
import com.staffly.backend.availability.DiaSemana;
import com.staffly.backend.availability.EmployeeAvailability;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.common.audit.AuditableFieldChangedEvent;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeResolver;
import com.staffly.backend.schedule.dto.CreateScheduleRequest;
import com.staffly.backend.schedule.dto.ScheduleResponse;
import com.staffly.backend.schedule.dto.UpdateScheduleRequest;
import com.staffly.backend.schedule.dto.UpdateStatusRequest;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final EmployeeResolver employeeResolver;
    private final BranchRepository branchRepository;
    private final AvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           EmployeeResolver employeeResolver,
                           BranchRepository branchRepository,
                           AvailabilityRepository availabilityRepository,
                           UserRepository userRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.scheduleRepository = scheduleRepository;
        this.employeeResolver = employeeResolver;
        this.branchRepository = branchRepository;
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(UUID employeeIdFilter, UUID branchIdFilter,
                                       LocalDate desde, LocalDate hasta,
                                       StafflyUserPrincipal principal) {
        List<Schedule> all = scheduleRepository.findByCompanyId(principal.getCompanyId());

        UUID effectiveEmpId = employeeIdFilter;
        if (principal.getRol() == Rol.EMPLOYEE) {
            effectiveEmpId = resolveOwnEmployeeId(principal);
        }
        final UUID finalEmpId = effectiveEmpId;

        return all.stream()
                .filter(s -> finalEmpId == null || s.getEmployee().getId().equals(finalEmpId))
                .filter(s -> branchIdFilter == null || s.getBranch().getId().equals(branchIdFilter))
                .filter(s -> desde == null || !s.getFechaHoraInicio().toLocalDate().isBefore(desde))
                .filter(s -> hasta == null || !s.getFechaHoraInicio().toLocalDate().isAfter(hasta))
                .filter(s -> principal.getRol() != Rol.SUPERVISOR
                        || principal.getBranchIds().contains(s.getBranch().getId()))
                .sorted(Comparator.comparing(Schedule::getFechaHoraInicio))
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request, StafflyUserPrincipal principal) {
        if (!request.fechaHoraFin().isAfter(request.fechaHoraInicio())) {
            throw new BadRequestException("fechaHoraFin debe ser posterior a fechaHoraInicio");
        }
        Employee employee = employeeResolver.resolveForCaller(request.employeeId(), principal, false);
        Branch branch = branchRepository.findByIdAndCompanyId(request.branchId(), principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada"));

        if (scheduleRepository.existsOverlap(principal.getCompanyId(), employee.getId(),
                request.fechaHoraInicio(), request.fechaHoraFin(), null)) {
            throw new ConflictException("El empleado ya tiene un turno en ese horario");
        }

        Schedule schedule = new Schedule();
        schedule.setCompanyId(principal.getCompanyId());
        schedule.setEmployee(employee);
        schedule.setBranch(branch);
        schedule.setFechaHoraInicio(request.fechaHoraInicio());
        schedule.setFechaHoraFin(request.fechaHoraFin());
        schedule.setTipoTurno(request.tipoTurno());
        schedule.setEstado(EstadoTurno.PLANIFICADO);
        schedule = scheduleRepository.save(schedule);

        String warning = checkDisponibilidad(schedule, principal.getCompanyId());
        if (warning != null) {
            eventPublisher.publishEvent(new AuditableFieldChangedEvent(
                    principal.getCompanyId(), "Schedule", schedule.getId(), principal.getUserId(),
                    "asignacion_fuera_disponibilidad", null, warning));
        }
        return ScheduleResponse.from(schedule, warning);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse findById(UUID id, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmpId = resolveOwnEmployeeId(principal);
            if (!schedule.getEmployee().getId().equals(ownEmpId)) {
                throw new ResourceNotFoundException("No se encontró el turno solicitado");
            }
        }
        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }
        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public ScheduleResponse update(UUID id, UpdateScheduleRequest request, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }

        if (request.branchId() != null) {
            Branch branch = branchRepository.findByIdAndCompanyId(request.branchId(), principal.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada"));
            schedule.setBranch(branch);
        }

        var inicioFinal = request.fechaHoraInicio() != null ? request.fechaHoraInicio() : schedule.getFechaHoraInicio();
        var finFinal = request.fechaHoraFin() != null ? request.fechaHoraFin() : schedule.getFechaHoraFin();

        if (!finFinal.isAfter(inicioFinal)) {
            throw new BadRequestException("fechaHoraFin debe ser posterior a fechaHoraInicio");
        }
        if (scheduleRepository.existsOverlap(principal.getCompanyId(), schedule.getEmployee().getId(),
                inicioFinal, finFinal, schedule.getId())) {
            throw new ConflictException("El empleado ya tiene un turno en ese horario");
        }

        schedule.setFechaHoraInicio(inicioFinal);
        schedule.setFechaHoraFin(finFinal);
        if (request.tipoTurno() != null) schedule.setTipoTurno(request.tipoTurno());
        schedule = scheduleRepository.save(schedule);

        String warning = checkDisponibilidad(schedule, principal.getCompanyId());
        if (warning != null) {
            eventPublisher.publishEvent(new AuditableFieldChangedEvent(
                    principal.getCompanyId(), "Schedule", schedule.getId(), principal.getUserId(),
                    "asignacion_fuera_disponibilidad", null, warning));
        }
        return ScheduleResponse.from(schedule, warning);
    }

    @Transactional
    public void delete(UUID id, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }
        if (schedule.getEstado() != EstadoTurno.PLANIFICADO) {
            throw new ConflictException("Solo se pueden eliminar turnos en estado PLANIFICADO");
        }
        scheduleRepository.delete(schedule);
    }

    @Transactional
    public ScheduleResponse confirm(UUID id, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }
        if (schedule.getEstado() != EstadoTurno.PLANIFICADO) {
            throw new ConflictException("Solo se pueden confirmar turnos en estado PLANIFICADO");
        }
        schedule.setEstado(EstadoTurno.CONFIRMADO);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleResponse updateStatus(UUID id, UpdateStatusRequest request, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }
        if (schedule.getEstado() != EstadoTurno.CONFIRMADO) {
            throw new ConflictException("El estado solo puede cambiarse desde CONFIRMADO");
        }
        EstadoTurno nuevo = request.estado();
        if (nuevo != EstadoTurno.CUMPLIDO && nuevo != EstadoTurno.AUSENTE) {
            throw new BadRequestException("Estado inválido: solo se permite CUMPLIDO o AUSENTE");
        }
        schedule.setEstado(nuevo);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    // ── disponibilidad ────────────────────────────────────────────────────────

    /**
     * Verifica que el turno esté completamente cubierto por la disponibilidad
     * declarada del empleado. Para turnos que cruzan medianoche, divide en dos
     * segmentos (día de inicio y día de fin) y verifica cada uno por separado.
     * Retorna "OUT_OF_AVAILABILITY" si falta cobertura, null si está cubierto.
     */
    private String checkDisponibilidad(Schedule schedule, UUID companyId) {
        var inicio = schedule.getFechaHoraInicio();
        var fin = schedule.getFechaHoraFin();

        boolean mismodia = inicio.toLocalDate().equals(fin.toLocalDate())
                || fin.toLocalTime().equals(LocalTime.MIDNIGHT);

        if (mismodia) {
            LocalTime tFin = fin.toLocalTime().equals(LocalTime.MIDNIGHT) ? LocalTime.MIDNIGHT : fin.toLocalTime();
            if (!isCovered(schedule.getEmployee().getId(), companyId,
                    inicio.getDayOfWeek(), inicio.toLocalTime(), tFin)) {
                return "OUT_OF_AVAILABILITY";
            }
        } else {
            // Segmento A: día de inicio, desde hora inicio hasta medianoche (1440 min)
            if (!isCovered(schedule.getEmployee().getId(), companyId,
                    inicio.getDayOfWeek(), inicio.toLocalTime(), LocalTime.MIDNIGHT)) {
                return "OUT_OF_AVAILABILITY";
            }
            // Segmento B: día de fin, desde medianoche hasta hora fin
            if (!isCovered(schedule.getEmployee().getId(), companyId,
                    fin.getDayOfWeek(), LocalTime.MIDNIGHT, fin.toLocalTime())) {
                return "OUT_OF_AVAILABILITY";
            }
        }
        return null;
    }

    /**
     * Retorna true si existe al menos una franja de disponibilidad del empleado
     * en el día indicado que contenga completamente el segmento [segInicio, segFin).
     * LocalTime.MIDNIGHT como segFin representa "fin de día" (1440 minutos).
     * LocalTime.MIDNIGHT como segInicio representa "inicio de día" (0 minutos).
     */
    private boolean isCovered(UUID employeeId, UUID companyId,
                               java.time.DayOfWeek dow, LocalTime segInicio, LocalTime segFin) {
        DiaSemana dia = DiaSemana.fromDayOfWeek(dow);
        List<EmployeeAvailability> franjas = availabilityRepository
                .findByCompanyIdAndEmployeeIdAndDiaSemana(companyId, employeeId, dia);
        if (franjas.isEmpty()) return false;

        int segInicioMin = toMinutes(segInicio);
        // LocalTime.MIDNIGHT como fin = 1440 (fin de día completo)
        int segFinMin = segFin.equals(LocalTime.MIDNIGHT) && !segInicio.equals(LocalTime.MIDNIGHT)
                ? 24 * 60
                : toMinutes(segFin);

        for (EmployeeAvailability franja : franjas) {
            int franjaInicioMin = toMinutes(franja.getHoraInicio());
            int franjaFinMin = franjaFinEnMinutos(franja.getHoraInicio(), franja.getHoraFin());
            if (franjaInicioMin <= segInicioMin && franjaFinMin >= segFinMin) {
                return true;
            }
        }
        return false;
    }

    private int toMinutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private int franjaFinEnMinutos(LocalTime inicio, LocalTime fin) {
        int min = toMinutes(fin);
        return min <= toMinutes(inicio) ? min + 24 * 60 : min;
    }

    private UUID resolveOwnEmployeeId(StafflyUserPrincipal principal) {
        return userRepository.findByIdAndCompanyId(principal.getUserId(), principal.getCompanyId())
                .map(User::getEmployee)
                .map(Employee::getId)
                .orElse(null);
    }
}
```

- [ ] **Step 4: Implementar ScheduleController**

```java
// backend/src/main/java/com/staffly/backend/schedule/ScheduleController.java
package com.staffly.backend.schedule;

import com.staffly.backend.schedule.dto.CreateScheduleRequest;
import com.staffly.backend.schedule.dto.ScheduleResponse;
import com.staffly.backend.schedule.dto.UpdateScheduleRequest;
import com.staffly.backend.schedule.dto.UpdateStatusRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR','EMPLOYEE')")
    public List<ScheduleResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.list(employeeId, branchId, desde, hasta, principal);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(
            @Valid @RequestBody CreateScheduleRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.create(request, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR','EMPLOYEE')")
    public ScheduleResponse findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.findById(id, principal);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    public ScheduleResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduleRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        scheduleService.delete(id, principal);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    public ScheduleResponse confirm(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.confirm(id, principal);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    public ScheduleResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.updateStatus(id, request, principal);
    }
}
```

- [ ] **Step 5: Correr los tests (GREEN)**

```bash
cd backend && ./mvnw test -pl . -Dtest=ScheduleControllerTest -q
```
Expected: `Tests run: 20, Failures: 0, Errors: 0` — si alguno falla, investigar y corregir el service/controller antes de continuar.

- [ ] **Step 6: Correr la suite completa para verificar que no hay regresiones**

```bash
cd backend && ./mvnw test 2>&1 | grep -E "Tests run|BUILD"
```
Expected: `Tests run: 112, Failures: 0, Errors: 0` (92 existentes + 20 nuevos). BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/staffly/backend/schedule/ScheduleService.java \
        backend/src/main/java/com/staffly/backend/schedule/ScheduleController.java \
        backend/src/test/java/com/staffly/backend/schedule/ScheduleControllerTest.java
git commit -m "feat: agregar crud de schedules con validaciones y scoping por rol"
```
