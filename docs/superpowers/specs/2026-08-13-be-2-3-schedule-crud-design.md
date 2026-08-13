# BE-2.3 — CRUD Schedule + solapamiento + advertencia de disponibilidad

**Fecha:** 2026-08-13  
**Issue:** #51  
**Rama:** `feature/schedule-crud-overlap`  
**Depende de:** BE-2.1 (EmployeeAvailability), BE-1.4 (Employee), BE-1.6 (Branch + SUPERVISOR scoping)

---

## Alcance

Implementar el módulo `schedule/` con CRUD completo de turnos, validación de solapamiento (bloqueo duro), advertencia de fuera-de-disponibilidad (suave), y cambios de estado. El endpoint `POST /schedules/{id}/duplicate-weekly` queda para BE-2.4. La validación contra licencias aprobadas (RF-15e) queda para BE-2.6.

Como parte de este issue: extraer `EmployeeResolver` como componente compartido y refactorizar `AvailabilityService` para eliminar la duplicación.

---

## 1. Refactor: EmployeeResolver

Se extrae un `@Component` en `employee/EmployeeResolver.java` con un único método público:

```java
Employee resolveForCaller(UUID employeeId, StafflyUserPrincipal principal, boolean allowEmployee)
```

**Capas de scoping (en orden):**
1. Tenant: `findByIdAndCompanyId` → 404 si no existe en la empresa
2. Si `allowEmployee = false` y el caller es EMPLOYEE → 403
3. Si `allowEmployee = true` y el caller es EMPLOYEE → 403 si el empleado no es el suyo propio
4. Si el caller es SUPERVISOR → 404 si el empleado no pertenece a ninguna de sus sucursales asignadas

**Servicios que lo usan tras el refactor:**
- `AvailabilityService` — `allowEmployee = true`
- `ScheduleService` — `allowEmployee = false` en write paths; GET /schedules/{id} maneja el scoping de EMPLOYEE directamente en el controller/service sin pasar por el resolver (el EMPLOYEE solo ve el schedule si es suyo, pero no accede por employeeId sino por scheduleId)

---

## 2. Entidad: Schedule

**Tabla:** `schedule` (migración V9)

| Campo | Tipo DB | Notas |
|---|---|---|
| `id` | UUID NOT NULL PK | `@GeneratedValue` |
| `company_id` | UUID NOT NULL FK→company | tenant, hereda de `TenantAwareEntity` |
| `employee_id` | UUID NOT NULL FK→employee | |
| `branch_id` | UUID NOT NULL FK→branch | |
| `fecha_hora_inicio` | TIMESTAMP NOT NULL | timestamp completo, sin zona (UTC en backend) |
| `fecha_hora_fin` | TIMESTAMP NOT NULL | timestamp completo |
| `tipo_turno` | VARCHAR(20) NOT NULL | enum `TipoTurno`: `FIJO`, `ROTATIVO` |
| `estado` | VARCHAR(20) NOT NULL | enum `EstadoTurno`: `PLANIFICADO`, `CONFIRMADO`, `CUMPLIDO`, `AUSENTE` |

`horas_totales` es un campo derivado calculado en el DTO (no almacenado): `Duration.between(fechaHoraInicio, fechaHoraFin).toMinutes() / 60.0`.

**Índice:** `idx_schedule_company_employee ON schedule(company_id, employee_id)` — soporta la query de solapamiento.

**Sin ON DELETE CASCADE** — consistente con V6 y V7.

---

## 3. Validación de solapamiento (RF-15) — bloqueo duro

Al crear o editar un turno, se verifica que no exista otro turno para el mismo empleado (en cualquier sucursal de la empresa) que se solape:

```
fecha_hora_inicio_existente < :nuevaFin AND fecha_hora_fin_existente > :nuevaInicio
```

En PATCH, el turno actual se excluye del chequeo (`AND id <> :excludeId`).

Si hay solapamiento → `409 Conflict`.

Implementado con `@Query` JPQL explícita en `ScheduleRepository.existsOverlap(UUID companyId, UUID employeeId, LocalDateTime inicio, LocalDateTime fin, UUID excludeId)`.

---

## 4. Validación de disponibilidad (RF-10) — advertencia suave

Para el turno `[fechaHoraInicio, fechaHoraFin)`:

**Caso 1 — mismo día calendario:**
- Obtener `DiaSemana` de `fechaHoraInicio`
- Verificar que existe al menos una franja de disponibilidad del empleado en ese día que contenga completamente `[time(inicio), time(fin))`

**Caso 2 — cruza medianoche (días distintos):**
- Segmento A: `DiaSemana(fechaHoraInicio)` → debe haber una franja que contenga `[time(inicio), medianoche)`
- Segmento B: `DiaSemana(fechaHoraFin)` → debe haber una franja que contenga `[medianoche, time(fin))`

**Cobertura de segmento:** usa la aritmética en minutos con wrap de `AvailabilityService` (`finEnMinutos`). Una franja de disponibilidad "contiene" un segmento `[t1, t2)` si `inicio_franja ≤ t1` y `fin_franja ≥ t2` en el espacio de minutos con wrap.

**Si el empleado no tiene ninguna franja cargada** → OUT_OF_AVAILABILITY (ausencia de datos no se asume como disponibilidad total).

**Si algún segmento no tiene cobertura:**
- El response incluye `"warning": "OUT_OF_AVAILABILITY"` (no null)
- Se publica un `AuditableFieldChangedEvent`:
  - `entityType = "Schedule"`, `campo = "asignacion_fuera_disponibilidad"`, `valorAnterior = null`, `valorNuevo = "OUT_OF_AVAILABILITY"`

El turno se crea/edita igualmente (no es un bloqueo).

---

## 5. Endpoints

### GET /schedules
**Roles:** ADMIN, RRHH, SUPERVISOR, EMPLOYEE  
**Filtros:** `?employeeId=` (UUID), `?branchId=` (UUID), `?desde=` (LocalDate), `?hasta=` (LocalDate)

Scoping:
- SUPERVISOR: solo schedules de empleados en sus sucursales asignadas
- EMPLOYEE: solo sus propios schedules (ignora `employeeId` del query param)

Orden: `fecha_hora_inicio ASC`.

Response: `List<ScheduleResponse>` (sin campo `warning` — es solo para create/update).

### POST /schedules
**Roles:** ADMIN, RRHH, SUPERVISOR  
**Body:**
```json
{
  "employeeId": "...",
  "branchId": "...",
  "fechaHoraInicio": "2026-07-10T22:00:00",
  "fechaHoraFin": "2026-07-11T06:00:00",
  "tipoTurno": "ROTATIVO"
}
```
Validaciones: solapamiento (409), disponibilidad (warning).  
Response `201`: `ScheduleResponse` con `warning` null o `"OUT_OF_AVAILABILITY"`.

### GET /schedules/{id}
**Roles:** ADMIN, RRHH, SUPERVISOR, EMPLOYEE  
EMPLOYEE: 404 si el schedule no le pertenece.  
SUPERVISOR: 404 si el empleado no es de sus sucursales.

### PATCH /schedules/{id}
**Roles:** ADMIN, RRHH, SUPERVISOR  
Campos patcheables: `branchId`, `fechaHoraInicio`, `fechaHoraFin`, `tipoTurno`.  
`employeeId` no es patcheable — cambiar el empleado asignado requiere delete + create.  
Mismas validaciones que POST (con self-exclusion en solapamiento).  
Response `200`: `ScheduleResponse` con `warning`.

### DELETE /schedules/{id}
**Roles:** ADMIN, RRHH, SUPERVISOR  
Solo si `estado = PLANIFICADO` → `409` si está en otro estado.

### POST /schedules/{id}/confirm
**Roles:** ADMIN, RRHH, SUPERVISOR  
Transición: `PLANIFICADO → CONFIRMADO`.  
`409` si ya está en otro estado.  
Response `200`: `ScheduleResponse`.

### PATCH /schedules/{id}/status
**Roles:** ADMIN, RRHH, SUPERVISOR  
Transición: `CONFIRMADO → CUMPLIDO | AUSENTE`.  
`409` si el estado actual no es `CONFIRMADO`.  
Body: `{ "estado": "CUMPLIDO" }` o `{ "estado": "AUSENTE" }`.  
Response `200`: `ScheduleResponse`.

---

## 6. ScheduleResponse DTO

```json
{
  "id": "...",
  "employeeId": "...",
  "branchId": "...",
  "fechaHoraInicio": "2026-07-10T22:00:00",
  "fechaHoraFin": "2026-07-11T06:00:00",
  "tipoTurno": "ROTATIVO",
  "estado": "PLANIFICADO",
  "horasTotales": 8.0,
  "warning": null
}
```

En GET /schedules (listado), `warning` siempre es `null` (no aplica en lectura).

---

## 7. Decisiones explícitas

| # | Decisión |
|---|---|
| 1 | `horas_totales` se computa en el DTO, no se almacena — evita campo derivado redundante |
| 2 | LeaveRequest blocking (RF-15e) fuera de alcance — se agrega en BE-2.6 |
| 3 | `duplicate-weekly` fuera de alcance — se agrega en BE-2.4 |
| 4 | EMPLOYEE no puede crear/editar/eliminar turnos — solo lectura de los propios |
| 5 | DELETE solo en estado PLANIFICADO — turno ya confirmado o cumplido no se borra directo |
| 6 | PATCH no permite cambiar `employeeId` — requiere delete + create |
| 7 | Disponibilidad sin datos → OUT_OF_AVAILABILITY (no se asume disponibilidad total) |
| 8 | Para turnos que cruzan medianoche: se valida disponibilidad en ambos días por separado |
| 9 | EmployeeResolver refactoriza también AvailabilityService (elimina la 2da copia existente) |

---

## 8. Tests de integración esperados

1. `adminCrudLifecycle` — crear, listar, obtener, patch, confirm, status, delete
2. `solapamientoMismaSucursal` — 409 al crear turno solapado en la misma sucursal
3. `solapamientoCrossBranch` — 409 al crear turno solapado en sucursal distinta de la misma empresa
4. `solapamientoCrossTenant` — no interfiere con turnos de otra empresa
5. `patchSelfExclusionSolapamiento` — PATCH del turno con mismos timestamps → no 409
6. `warningFueraDeDisponibilidad` — 201 con warning cuando el turno cae fuera de disponibilidad
7. `sinWarningDentroDeDisponibilidad` — 201 sin warning cuando el turno cae dentro de disponibilidad
8. `warningTurnoCruzaMedianoche` — warning cuando un segmento no tiene cobertura
9. `auditLogCreadoConWarning` — se persiste el AuditableFieldChangedEvent cuando hay warning
10. `supervisorSoloVeSusSucursales` — listado filtrado por sucursales del SUPERVISOR
11. `employeeSoloVeLosSuyos` — EMPLOYEE no ve turnos de otros empleados
12. `employeeNoAccedeGetAjenoById` — EMPLOYEE GET /schedules/{id} de otro → 404
13. `empleadoFueraDeAlcanceSupervisor` — POST con empleado de otra sucursal → 404
14. `deleteEnPlanificado` — 204 OK
15. `deleteNoEnPlanificado` — 409 si CONFIRMADO
16. `confirmTransicion` — PLANIFICADO → CONFIRMADO OK, idempotencia → 409
17. `statusTransicion` — CONFIRMADO → CUMPLIDO, AUSENTE OK; desde PLANIFICADO → 409
18. `crossTenantIsolacion` — schedule de otra empresa → 404
19. `filtroDesdeHasta` — lista filtrada por rango de fechas
20. `rrhhPuedeEscribir` — RRHH puede crear/editar/eliminar turnos
