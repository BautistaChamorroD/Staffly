# BE-2.1 — CRUD `EmployeeAvailability`

**Issue**: [#49](https://github.com/BautistaChamorroD/Staffly/issues/49), rama `feature/availability-crud`. Depende de BE-1.6 (mergeado). Primer issue de la Fase 2; BE-2.3 (Schedule) consume esta tabla para calcular el warning `OUT_OF_AVAILABILITY`.

## Contexto

Franjas de disponibilidad horaria declaradas por el propio empleado, sin aprobación (RF-08, decisión #5 del documento de requerimientos: se toman como válidas al cargarse). RRHH/Supervisor las leen al armar horarios (RF-09). Primer módulo nuevo del backend desde Fase 1 — replica los patrones ya establecidos: entidad tenant-aware con `@Filter`, `findByIdAndCompanyId` para lookups, scoping de SUPERVISOR por `branchIds` del JWT, 404 para recursos de otro tenant.

**Decisiones de diseño tomadas con el usuario (2026-07-18):**

1. **Solo recurrencia semanal.** El texto de requerimientos menciona "excepciones puntuales" por fecha específica, pero el ERD solo modela `dia_semana` — se implementa el ERD (YAGNI). Si el cliente real pide excepciones, se agregan después.
2. **Cruce de medianoche por semántica de wrap**: `hora_fin < hora_inicio` es válido y significa que la franja sigue hasta el día siguiente (viernes 20:00–02:00 = hasta sábado 02:00).
3. **Solapamiento entre franjas del mismo día → 409.** El chequeo es por `dia_semana` del mismo empleado; el edge del wrap contra el día siguiente (viernes 20–02 vs sábado 01–05) no se detecta — aceptado, es raro e inofensivo (la disponibilidad efectiva es una unión).

**Desvíos deliberados de los docs:**

- Sin campo `estado` (el texto de requerimientos lo lista "si se requiere validación de RRHH" — la decisión #5 ya lo descartó).
- Sin campo `tipo` (el ERD lo tiene, pero con un único valor posible — recurrente semanal — no discrimina nada; se agrega si entran las excepciones puntuales).

## Modelo y persistencia

`backend/availability/` (módulo de dominio nuevo, misma estructura que `branch/`):

```
EmployeeAvailability (tabla employee_availability) extends TenantAwareEntity
├── id            UUID PK (@GeneratedValue UUID)
├── company_id    UUID NOT NULL (heredado, @Filter tenantFilter)
├── employee_id   UUID NOT NULL FK → employee (@ManyToOne LAZY)
├── dia_semana    VARCHAR(10) NOT NULL — enum DiaSemana { LUNES, MARTES, MIERCOLES, JUEVES, VIERNES, SABADO, DOMINGO }
├── hora_inicio   TIME NOT NULL
└── hora_fin      TIME NOT NULL
```

Migración `V6__create_employee_availability.sql` (V4 y V5 ya existen en `main`): tabla + FK a `employee` y `company` + índice `(employee_id)`.

`DiaSemana` vive en `availability/` (convención: el enum junto a la entidad que describe). No se reusa `java.time.DayOfWeek` — los nombres de dominio van en español, consistente con `estado_laboral` etc., y evita depender del orden ISO para serialización.

`AvailabilityRepository`: `findByIdAndCompanyId(id, companyId)`, `findByCompanyIdAndEmployeeId(companyId, employeeId)`, `findByCompanyIdAndEmployeeIdAndDiaSemana(companyId, employeeId, diaSemana)` (para el chequeo de solape). **El orden del listado lo aplica el service** (comparador por ordinal de `DiaSemana` + `horaInicio`): el enum se persiste como STRING, así que un `ORDER BY dia_semana` en SQL sería alfabético (DOMINGO primero), no semanal.

## API (contrato de `api-design.md` §6, sin cambios)

Prefijo `/api/v1/employees/{employeeId}/availability`, `company_id` siempre del JWT:

| Método | Path | Roles | Request | Response |
|---|---|---|---|---|
| GET | `/` | ADMIN, RRHH, SUPERVISOR, EMPLOYEE (el propio) | — | `AvailabilityResponse[]` |
| POST | `/` | ADMIN, RRHH, EMPLOYEE (el propio) | `CreateAvailabilityRequest` | `201` `AvailabilityResponse` |
| PATCH | `/{id}` | ADMIN, RRHH, EMPLOYEE (el propio) | `UpdateAvailabilityRequest` | `AvailabilityResponse` |
| DELETE | `/{id}` | ADMIN, RRHH, EMPLOYEE (el propio) | — | `204` |

```java
record AvailabilityResponse(UUID id, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin)

record CreateAvailabilityRequest(@NotNull DiaSemana diaSemana, @NotNull LocalTime horaInicio, @NotNull LocalTime horaFin)

// Actualización parcial: nulls se dejan sin tocar (mismo patrón que el resto de los PATCH).
record UpdateAvailabilityRequest(DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin)
```

El GET ordena por día (LUNES primero, comparador en el service — ver nota del repositorio) y hora de inicio — orden estable para FE-2.1 sin lógica extra en el cliente.

## Autorización y scoping

Capas, de afuera hacia adentro:

1. **`@PreAuthorize`**: GET `hasAnyRole('ADMIN','RRHH','SUPERVISOR','EMPLOYEE')`; POST/PATCH/DELETE `hasAnyRole('ADMIN','RRHH','EMPLOYEE')`. SUPERVISOR escribe → 403 directo.
2. **Tenant**: el `Employee` del path se resuelve con `findByIdAndCompanyId` → empleado de otra empresa = 404 `RESOURCE_NOT_FOUND` (nunca 403, patrón de Fase 1). Toda franja se valida además contra `employee_id` (una franja de otro empleado bajo un `employeeId` ajeno en la URL → 404).
3. **EMPLOYEE**: solo su propio registro (RF-29). El empleado propio se resuelve vía `user.getEmployee()` (mismo lookup que `EmployeeService.getMe`). Si `employeeId` del path ≠ su propio id → **403** `ACCESS_DENIED` (`AccessDeniedException` — la nota RF-29 de api-design pide 403 explícito acá: el rol tiene acceso al endpoint pero no a ese recurso; no es un caso de ocultamiento entre tenants). Un user EMPLOYEE sin `Employee` vinculado → 403 también.
4. **SUPERVISOR** (solo GET): el empleado debe tener alguna sucursal en `principal.getBranchIds()` → si no, 404 (mismo criterio que `EmployeeService.isInScope`).

## Validaciones

- Bean Validation: los tres campos requeridos en el create (`@NotNull`).
- `hora_inicio == hora_fin` → 400 `VALIDATION_ERROR` (`BadRequestException`, "franja vacía"). Aplica en create y en el estado final del PATCH parcial (igual que `validarFechas` de Employee: se valida contra el valor entrante o el guardado según qué llegue).
- `hora_fin < hora_inicio` → válido, cruza medianoche.
- **Solapamiento** → 409 `CONFLICT` (`ConflictException`): la franja nueva/editada se compara contra las existentes del mismo `dia_semana` del mismo empleado (en PATCH, excluyéndose a sí misma). Normalización para comparar: intervalos en minutos `[inicio, fin)`, con `fin += 24h` si `fin <= inicio` (wrap). Dos intervalos solapan si `inicioA < finB && inicioB < finA`.

## Fuera de alcance

- Excepciones puntuales por fecha específica (decisión #1 de este spec).
- El warning `OUT_OF_AVAILABILITY` y su registro en auditoría — eso es BE-2.3 (RF-10 audita la *asignación de turnos*, no este CRUD).
- Pantalla de disponibilidad — FE-2.1.
- Detección de solape entre el wrap de un día y las franjas del día siguiente (decisión #3).

## Testing

`AvailabilityControllerTest` (patrón dos-empresas de Fase 1, `@SpringBootTest` + MockMvc + `@Transactional`):

- CRUD feliz del propio EMPLOYEE (crea, lista ordenada, edita parcial, borra).
- ADMIN y RRHH operan sobre la disponibilidad de cualquier empleado de su empresa.
- EMPLOYEE contra el `employeeId` de otro empleado → 403 `ACCESS_DENIED`.
- EMPLOYEE sin `Employee` vinculado → 403.
- SUPERVISOR: GET de un empleado de sus sucursales → 200; de un empleado fuera de su alcance → 404; POST → 403.
- Cross-tenant: `employeeId` de otra empresa → 404; franja de otro empleado bajo el propio `employeeId` → 404.
- Solape mismo día → 409 (create y update); franja pegada sin solape (9–12 y 12–15) → 201.
- Cruce de medianoche (20:00–02:00) → 201; y solape contra otra franja del mismo día con wrap (22:00–03:00 vs 20:00–02:00) → 409.
- `hora_inicio == hora_fin` → 400.
