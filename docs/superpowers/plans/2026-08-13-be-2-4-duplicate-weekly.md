# BE-2.4: POST /schedules/{id}/duplicate-weekly — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar `POST /schedules/{id}/duplicate-weekly` que duplica un turno fuente a todas las ocurrencias del mismo día de semana en un mes objetivo, fallando atómicamente si cualquier ocurrencia solapa un turno existente.

**Architecture:** Un único método de servicio + endpoint en el controller. La verificación de solapamiento recopila **todos** los conflictos antes de lanzar la excepción (comportamiento atómico). La disponibilidad se verifica por copia con la misma lógica OOA que `create`. El cuerpo del error de solapamiento lleva detalle estructurado (fecha + UUID del turno en conflicto) en lugar de un genérico 409.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, JUnit 5, MockMvc.

## Global Constraints

- `company_id` siempre del JWT (`StafflyUserPrincipal`), nunca del body ni de la URL.
- `@PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")` — EMPLOYEE recibe 403.
- SUPERVISOR → 404 si `source.getBranch().getId()` no está en `principal.getBranchIds()`.
- La verificación de solapamiento es **ATÓMICA**: recopilar TODOS los conflictos antes de lanzar. Nunca crear parcialmente.
- El cuerpo del 409 usa código `SCHEDULE_OVERLAP_BATCH` e incluye lista `conflictos` con `fecha` (LocalDate, formato ISO) y `turnoExistenteId` (UUID) por cada conflicto.
- La fecha del turno fuente se excluye de las fechas objetivo (evita auto-conflicto cuando source y target son el mismo mes).
- Disponibilidad: mismo método privado `checkDisponibilidad`. OOA → `AuditableFieldChangedEvent` por copia + `advertencia: "OUT_OF_AVAILABILITY"` en la respuesta.
- Las copias heredan del fuente: `employee`, `branch`, `tipoTurno`. `estado` = `PLANIFICADO`.
- Para turnos que cruzan medianoche, preservar el offset de días: `newFin = targetDate.plusDays(dayDelta).atTime(srcFinTime)` donde `dayDelta = ChronoUnit.DAYS.between(src.inicio.toLocalDate(), src.fin.toLocalDate())`.
- Conventional Commits en español. Commit después de que los tests pasen.

---

### Task 1: endpoint duplicate-weekly (TDD)

**Files:**
- Create: `backend/src/main/java/com/staffly/backend/schedule/dto/DuplicateWeeklyRequest.java`
- Create: `backend/src/main/java/com/staffly/backend/schedule/dto/DuplicateWeeklyResponse.java`
- Create: `backend/src/main/java/com/staffly/backend/common/ScheduleOverlapBatchException.java`
- Modify: `backend/src/main/java/com/staffly/backend/common/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/staffly/backend/schedule/ScheduleRepository.java`
- Modify: `backend/src/main/java/com/staffly/backend/schedule/ScheduleService.java`
- Modify: `backend/src/main/java/com/staffly/backend/schedule/ScheduleController.java`
- Modify: `backend/src/test/java/com/staffly/backend/schedule/ScheduleControllerTest.java`

**Interfaces:**
- Consumes: `ScheduleRepository.existsOverlap` (existente), `checkDisponibilidad` (método privado existente en ScheduleService), `AuditableFieldChangedEvent` (existente)
- Produces: `POST /api/v1/schedules/{id}/duplicate-weekly` → 201 `DuplicateWeeklyResponse` | 409 `{code, message, conflictos}`

---

- [ ] **Step 1: Agregar 9 tests que fallan a ScheduleControllerTest**

Agregar estos tests al final de `ScheduleControllerTest.java`, antes del `}` de cierre de la clase. Nota: 2026-07-06 y 2026-08-03 son lunes.

```java
// ── BE-2.4: duplicate-weekly ──────────────────────────────────────────────

@Test
void duplicarSemanal_exitoso_creaTodasLasCopias() throws Exception {
    // source: lunes 2026-07-06
    UUID sourceId = postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

    String body = objectMapper.writeValueAsString(Map.of("mesObjetivo", 8, "anioObjetivo", 2026));
    String resp = mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    var tree = objectMapper.readTree(resp);
    // lunes de agosto 2026: 3, 10, 17, 24, 31 — ninguno es 2026-07-06
    assertThat(tree.get("turnosCreados").size()).isEqualTo(5);
    assertThat(tree.get("advertencia").isNull()).isTrue();
}

@Test
void duplicarSemanal_mismoMes_omiteOcurrenciaOriginal() throws Exception {
    // source: lunes 2026-08-03 (primer lunes de agosto)
    UUID sourceId = postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-08-03T09:00:00", "2026-08-03T17:00:00", "FIJO");

    String body = objectMapper.writeValueAsString(Map.of("mesObjetivo", 8, "anioObjetivo", 2026));
    String resp = mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    // Aug 3 es el source → se omite; quedan Aug 10, 17, 24, 31 = 4 copias
    assertThat(objectMapper.readTree(resp).get("turnosCreados").size()).isEqualTo(4);
}

@Test
void duplicarSemanal_overlap_unaFecha_fallaTodo() throws Exception {
    UUID sourceId = postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");
    // conflicto exacto en lunes 2026-08-10
    UUID conflictId = postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-08-10T09:00:00", "2026-08-10T17:00:00", "FIJO");

    String body = objectMapper.writeValueAsString(Map.of("mesObjetivo", 8, "anioObjetivo", 2026));
    String resp = mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(body))
            .andExpect(status().isConflict())
            .andReturn().getResponse().getContentAsString();

    var tree = objectMapper.readTree(resp);
    assertThat(tree.get("code").asText()).isEqualTo("SCHEDULE_OVERLAP_BATCH");
    assertThat(tree.get("conflictos").size()).isEqualTo(1);
    assertThat(tree.get("conflictos").get(0).get("fecha").asText()).isEqualTo("2026-08-10");
    assertThat(tree.get("conflictos").get(0).get("turnoExistenteId").asText())
            .isEqualTo(conflictId.toString());
}

@Test
void duplicarSemanal_overlap_variasFechas_reportaTodasLasColisiones() throws Exception {
    UUID sourceId = postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");
    // conflictos en Aug 10 y Aug 17
    postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-08-10T09:00:00", "2026-08-10T17:00:00", "FIJO");
    postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-08-17T09:00:00", "2026-08-17T17:00:00", "FIJO");

    String body = objectMapper.writeValueAsString(Map.of("mesObjetivo", 8, "anioObjetivo", 2026));
    String resp = mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(body))
            .andExpect(status().isConflict())
            .andReturn().getResponse().getContentAsString();

    assertThat(objectMapper.readTree(resp).get("conflictos").size()).isEqualTo(2);
}

@Test
void duplicarSemanal_fueraDeDisponibilidad_creaConAdvertencia() throws Exception {
    // emp1 sin disponibilidad declarada
    UUID sourceId = postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

    String body = objectMapper.writeValueAsString(Map.of("mesObjetivo", 8, "anioObjetivo", 2026));
    String resp = mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    var tree = objectMapper.readTree(resp);
    assertThat(tree.get("turnosCreados").size()).isEqualTo(5);
    assertThat(tree.get("advertencia").asText()).isEqualTo("OUT_OF_AVAILABILITY");
    em.flush();
    // verificar AuditLog por copia (no por el source, filtrar por IDs de copias)
    var copiaIds = new java.util.HashSet<UUID>();
    tree.get("turnosCreados").forEach(n -> copiaIds.add(UUID.fromString(n.get("id").asText())));
    long auditCount = auditLogRepository.findAll().stream()
            .filter(l -> l.getEntityType().equals("Schedule")
                    && copiaIds.contains(l.getEntityId())
                    && l.getCampo().equals("asignacion_fuera_disponibilidad"))
            .count();
    assertThat(auditCount).isEqualTo(5);
}

@Test
void duplicarSemanal_scheduleNoExiste_404() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("mesObjetivo", 8, "anioObjetivo", 2026));
    mockMvc.perform(post(BASE_URL + "/" + UUID.randomUUID() + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(body))
            .andExpect(status().isNotFound());
}

@Test
void duplicarSemanal_supervisorFueraDeSuSucursal_404() throws Exception {
    // source en branch2 — fuera del scope del supervisor (que solo gestiona branch1)
    UUID sourceId = postSchedule(adminToken, emp2.getId(), branch2.getId(),
            "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

    String body = objectMapper.writeValueAsString(Map.of("mesObjetivo", 8, "anioObjetivo", 2026));
    mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + supervisorToken)
                    .contentType("application/json").content(body))
            .andExpect(status().isNotFound());
}

@Test
void duplicarSemanal_employee_403() throws Exception {
    UUID sourceId = postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

    String body = objectMapper.writeValueAsString(Map.of("mesObjetivo", 8, "anioObjetivo", 2026));
    mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + empToken1)
                    .contentType("application/json").content(body))
            .andExpect(status().isForbidden());
}

@Test
void duplicarSemanal_mesInvalido_400() throws Exception {
    UUID sourceId = postSchedule(adminToken, emp1.getId(), branch1.getId(),
            "2026-07-06T09:00:00", "2026-07-06T17:00:00", "FIJO");

    // mes = 0 → 400
    mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(Map.of("mesObjetivo", 0, "anioObjetivo", 2026))))
            .andExpect(status().isBadRequest());

    // mes = 13 → 400
    mockMvc.perform(post(BASE_URL + "/" + sourceId + "/duplicate-weekly")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(Map.of("mesObjetivo", 13, "anioObjetivo", 2026))))
            .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Correr los tests — verificar que los 9 nuevos fallan**

```bash
cd backend && ./mvnw test -Dtest="ScheduleControllerTest#duplicarSemanal*" -q 2>&1 | tail -20
```

Expected: BUILD FAILURE, 9 test failures (el endpoint aún no existe).

- [ ] **Step 3: Crear `DuplicateWeeklyRequest.java`**

Archivo: `backend/src/main/java/com/staffly/backend/schedule/dto/DuplicateWeeklyRequest.java`

```java
package com.staffly.backend.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record DuplicateWeeklyRequest(
        @Min(value = 1, message = "El mes debe estar entre 1 y 12")
        @Max(value = 12, message = "El mes debe estar entre 1 y 12")
        int mesObjetivo,

        @Positive(message = "El año debe ser positivo")
        int anioObjetivo
) {}
```

- [ ] **Step 4: Crear `DuplicateWeeklyResponse.java`**

Archivo: `backend/src/main/java/com/staffly/backend/schedule/dto/DuplicateWeeklyResponse.java`

```java
package com.staffly.backend.schedule.dto;

import java.util.List;

public record DuplicateWeeklyResponse(
        List<ScheduleResponse> turnosCreados,
        String advertencia
) {}
```

- [ ] **Step 5: Crear `ScheduleOverlapBatchException.java`**

Archivo: `backend/src/main/java/com/staffly/backend/common/ScheduleOverlapBatchException.java`

```java
package com.staffly.backend.common;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ScheduleOverlapBatchException extends RuntimeException {

    public record ConflictDetail(LocalDate fecha, UUID turnoExistenteId) {}

    private final List<ConflictDetail> conflictos;

    public ScheduleOverlapBatchException(List<ConflictDetail> conflictos) {
        super("El empleado ya tiene turnos en las fechas indicadas");
        this.conflictos = List.copyOf(conflictos);
    }

    public List<ConflictDetail> getConflictos() {
        return conflictos;
    }
}
```

- [ ] **Step 6: Agregar handler en `GlobalExceptionHandler.java`**

Agregar estos imports después de los existentes:
```java
import com.staffly.backend.common.ScheduleOverlapBatchException;
import java.util.Map;
```

Agregar este método después de `handleConflict` (línea ~59):

```java
@ExceptionHandler(ScheduleOverlapBatchException.class)
public ResponseEntity<Map<String, Object>> handleOverlapBatch(ScheduleOverlapBatchException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "code", "SCHEDULE_OVERLAP_BATCH",
            "message", ex.getMessage(),
            "conflictos", ex.getConflictos()
    ));
}
```

- [ ] **Step 7: Agregar `findConflictingIds` en `ScheduleRepository.java`**

Agregar import al inicio de la clase:
```java
import org.springframework.data.domain.Pageable;
```

Agregar este método después de `existsOverlap`:

```java
/**
 * Retorna el ID del primer turno que solapa [inicio, fin) para el empleado.
 * Usar con PageRequest.of(0, 1) para obtener solo uno.
 */
@Query("""
    SELECT s.id FROM Schedule s
    WHERE s.companyId = :companyId
      AND s.employee.id = :employeeId
      AND s.fechaHoraInicio < :fin
      AND s.fechaHoraFin > :inicio
    ORDER BY s.fechaHoraInicio ASC
""")
List<UUID> findConflictingIds(
        @Param("companyId") UUID companyId,
        @Param("employeeId") UUID employeeId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fin") LocalDateTime fin,
        Pageable pageable);
```

- [ ] **Step 8: Agregar método `duplicateWeekly` en `ScheduleService.java`**

Agregar estos imports (después de los existentes):
```java
import com.staffly.backend.common.ScheduleOverlapBatchException;
import com.staffly.backend.schedule.dto.DuplicateWeeklyRequest;
import com.staffly.backend.schedule.dto.DuplicateWeeklyResponse;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
```

Agregar este método en `ScheduleService` después de `updateStatus` y antes del comentario `// ── disponibilidad ──`:

```java
@Transactional
public DuplicateWeeklyResponse duplicateWeekly(UUID sourceId,
                                                DuplicateWeeklyRequest request,
                                                StafflyUserPrincipal principal) {
    Schedule source = scheduleRepository.findByIdAndCompanyId(sourceId, principal.getCompanyId())
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

    if (principal.getRol() == Rol.SUPERVISOR
            && !principal.getBranchIds().contains(source.getBranch().getId())) {
        throw new ResourceNotFoundException("No se encontró el turno solicitado");
    }

    DayOfWeek weekday = source.getFechaHoraInicio().getDayOfWeek();
    YearMonth targetMonth = YearMonth.of(request.anioObjetivo(), request.mesObjetivo());
    LocalDate sourceDate = source.getFechaHoraInicio().toLocalDate();
    long dayDelta = ChronoUnit.DAYS.between(sourceDate, source.getFechaHoraFin().toLocalDate());

    record Copy(LocalDate date, LocalDateTime inicio, LocalDateTime fin) {}
    List<Copy> copies = new ArrayList<>();
    for (LocalDate d = targetMonth.atDay(1); !d.isAfter(targetMonth.atEndOfMonth()); d = d.plusDays(1)) {
        if (d.getDayOfWeek() == weekday && !d.equals(sourceDate)) {
            copies.add(new Copy(d,
                    d.atTime(source.getFechaHoraInicio().toLocalTime()),
                    d.plusDays(dayDelta).atTime(source.getFechaHoraFin().toLocalTime())));
        }
    }

    if (copies.isEmpty()) {
        return new DuplicateWeeklyResponse(List.of(), null);
    }

    // Verificación atómica: recopilar todos los conflictos antes de rechazar
    List<ScheduleOverlapBatchException.ConflictDetail> conflictos = new ArrayList<>();
    for (Copy copy : copies) {
        List<UUID> ids = scheduleRepository.findConflictingIds(
                principal.getCompanyId(), source.getEmployee().getId(),
                copy.inicio(), copy.fin(), PageRequest.of(0, 1));
        if (!ids.isEmpty()) {
            conflictos.add(new ScheduleOverlapBatchException.ConflictDetail(copy.date(), ids.get(0)));
        }
    }
    if (!conflictos.isEmpty()) {
        throw new ScheduleOverlapBatchException(conflictos);
    }

    // Crear todas las copias
    List<Schedule> saved = new ArrayList<>();
    for (Copy copy : copies) {
        Schedule s = new Schedule();
        s.setCompanyId(principal.getCompanyId());
        s.setEmployee(source.getEmployee());
        s.setBranch(source.getBranch());
        s.setFechaHoraInicio(copy.inicio());
        s.setFechaHoraFin(copy.fin());
        s.setTipoTurno(source.getTipoTurno());
        s.setEstado(EstadoTurno.PLANIFICADO);
        saved.add(scheduleRepository.save(s));
    }

    // Disponibilidad — mismo resultado para todas las copias (mismo día de semana y hora)
    String warning = checkDisponibilidad(saved.get(0), principal.getCompanyId());
    for (Schedule s : saved) {
        if (warning != null) {
            eventPublisher.publishEvent(new AuditableFieldChangedEvent(
                    principal.getCompanyId(), "Schedule", s.getId(), principal.getUserId(),
                    "asignacion_fuera_disponibilidad", null, warning));
        }
    }

    return new DuplicateWeeklyResponse(
            saved.stream().map(s -> ScheduleResponse.from(s, warning)).toList(),
            warning);
}
```

- [ ] **Step 9: Agregar endpoint en `ScheduleController.java`**

Agregar imports:
```java
import com.staffly.backend.schedule.dto.DuplicateWeeklyRequest;
import com.staffly.backend.schedule.dto.DuplicateWeeklyResponse;
```

Agregar este endpoint después de `updateStatus`:

```java
@PostMapping("/{id}/duplicate-weekly")
@PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
@ResponseStatus(HttpStatus.CREATED)
public DuplicateWeeklyResponse duplicateWeekly(
        @PathVariable UUID id,
        @Valid @RequestBody DuplicateWeeklyRequest request,
        @AuthenticationPrincipal StafflyUserPrincipal principal) {
    return scheduleService.duplicateWeekly(id, request, principal);
}
```

- [ ] **Step 10: Correr el suite completo**

```bash
cd backend && ./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`. Los 9 nuevos tests + los 23 existentes = 32 tests en `ScheduleControllerTest`. Suite completo verde.

- [ ] **Step 11: Commit**

```bash
cd backend
git add src/main/java/com/staffly/backend/schedule/dto/DuplicateWeeklyRequest.java
git add src/main/java/com/staffly/backend/schedule/dto/DuplicateWeeklyResponse.java
git add src/main/java/com/staffly/backend/common/ScheduleOverlapBatchException.java
git add src/main/java/com/staffly/backend/common/GlobalExceptionHandler.java
git add src/main/java/com/staffly/backend/schedule/ScheduleRepository.java
git add src/main/java/com/staffly/backend/schedule/ScheduleService.java
git add src/main/java/com/staffly/backend/schedule/ScheduleController.java
git add src/test/java/com/staffly/backend/schedule/ScheduleControllerTest.java
git commit -m "feat: agregar endpoint de duplicado semanal de turnos con validación atómica de solapamientos"
```
