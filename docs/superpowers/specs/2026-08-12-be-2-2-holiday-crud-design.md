# BE-2.2 — CRUD `Holiday`

**Issue**: [#50](https://github.com/BautistaChamorroD/Staffly/issues/50), rama `feature/holiday-crud`. Depende de BE-1.3 (branches) y BE-1.4 (employees). Módulo de feriados por empresa, opcionalmente por sucursal.

## Contexto

Feriados configurados por empresa, usados en dos contextos: (1) como referencia en la vista de horarios para que RRHH/Supervisor vea qué días son feriado al armar turnos, y (2) como dato de entrada en el cálculo de nómina (RF-17: horas trabajadas en feriado reciben un multiplicador configurado en `PayrollConfig`). El CRUD es de baja complejidad, roles de escritura restringidos a ADMIN, lectura abierta a ADMIN, RRHH y SUPERVISOR.

**Decisiones de diseño tomadas con el usuario (2026-08-12):**

1. **Sin proyección de recurrencia**: `recurrente=true` es solo un flag informativo almacenado. El filtro `?anio=` aplica `fecha BETWEEN {anio}-01-01 AND {anio}-12-31` sobre la fecha real almacenada — sin expandir feriados recurrentes a otros años. El cliente que quiera ver un feriado recurrente en 2027 lo busca con `?anio=2027`; si no existe ese registro, no aparece. YAGNI.
2. **Duplicado por (company_id, branch_id, fecha)** → 409. Un feriado global (`branch_id IS NULL`) y uno específico de sucursal en la misma fecha pueden coexistir — son ámbitos distintos. Dos globales el mismo día o dos específicos de la misma sucursal el mismo día no.
3. **Fechas pasadas permitidas**: un feriado puede cargarse con fecha pasada (necesario para el cálculo de nómina histórica).

## Modelo y persistencia

`backend/holiday/` — mismo esquema de módulo que `availability/`:

```
Holiday (tabla holiday) extends TenantAwareEntity
├── id            UUID PK (@GeneratedValue UUID)
├── company_id    UUID NOT NULL (heredado de TenantAwareEntity, @Filter tenantFilter)
├── branch_id     UUID nullable FK → branch (@ManyToOne LAZY, optional=true)
├── fecha         DATE NOT NULL
├── nombre        VARCHAR(255) NOT NULL
└── recurrente    BOOLEAN NOT NULL DEFAULT false
```

Migración `V7__create_holiday.sql`:
- Tabla `holiday` con las columnas arriba.
- FK a `company` y a `branch` (con `ON DELETE CASCADE` en ambas — si se elimina una empresa o sucursal, sus feriados se eliminan en cascada a nivel DB).
- Índice `(company_id, fecha)` para las queries de nómina futura.

`HolidayRepository`: `findByIdAndCompanyId`, más queries de listado (ver Servicio).

## API (contrato de `api-design.md` §8, sin cambios)

Prefijo `/api/v1/holidays`, `company_id` siempre del JWT:

| Método | Path | Roles | Request | Response |
|---|---|---|---|---|
| GET | `/` | ADMIN, RRHH, SUPERVISOR | `?branchId=` (opt), `?anio=` (opt) | `HolidayResponse[]` |
| POST | `/` | ADMIN | `CreateHolidayRequest` | `201` `HolidayResponse` |
| PATCH | `/{id}` | ADMIN | `UpdateHolidayRequest` | `HolidayResponse` |
| DELETE | `/{id}` | ADMIN | — | `204` |

No hay `GET /holidays/{id}` — no está en el contrato de `api-design.md`.

```java
record HolidayResponse(UUID id, UUID branchId, LocalDate fecha, String nombre, boolean recurrente)

record CreateHolidayRequest(
    UUID branchId,                       // opcional — null = feriado global de la empresa
    @NotNull LocalDate fecha,
    @NotBlank String nombre,
    boolean recurrente                   // default false si se omite
)

// Actualización parcial: null se deja sin tocar (mismo patrón que el resto de los PATCH).
// recurrente es Boolean boxeado (no boolean primitivo) para distinguir null = "no cambiar" de false = "poner en false".
// branchId: null = no cambiar. No es posible convertir un feriado de branch-específico a global vía PATCH — delete + recreate.
record UpdateHolidayRequest(UUID branchId, LocalDate fecha, String nombre, Boolean recurrente)
```

### Semántica del filtro `?branchId=`

- Sin `?branchId=`: devuelve todos los feriados visibles para el rol (ver Autorización).
- Con `?branchId=X`: devuelve feriados globales (`branch_id IS NULL`) **más** feriados específicos de esa sucursal. Esto cubre el caso de uso principal: al abrir la vista de una sucursal, ver qué días son feriado (sean globales o específicos de esa sucursal).

El listado se ordena por `fecha ASC`.

## Autorización y scoping

Capas, de afuera hacia adentro:

1. **`@PreAuthorize`**: GET `hasAnyRole('ADMIN','RRHH','SUPERVISOR')`; POST/PATCH/DELETE `hasRole('ADMIN')`. EMPLOYEE no tiene acceso a ningún endpoint de `/holidays`.
2. **Tenant**: todos los lookups usan `findByIdAndCompanyId` → feriado de otra empresa = 404.
3. **SUPERVISOR (solo GET)**:
   - Ve siempre los feriados globales de su empresa (`branch_id IS NULL`).
   - Ve feriados específicos solo de sus sucursales (`principal.getBranchIds()`).
   - Si provee `?branchId=X` de una sucursal fuera de su alcance → **404** `RESOURCE_NOT_FOUND` (mismo patrón que employee out-of-scope en BE-2.1).
4. **`branchId` en create/update**: si se provee, debe pertenecer a la empresa del JWT → validar con `findByIdAndCompanyId` en `BranchRepository`; si no existe → 404.

## Validaciones

- Bean Validation: `@NotNull fecha`, `@NotBlank nombre` en el create.
- `nombre` vacío o en blanco en el estado final del PATCH → 400 `VALIDATION_ERROR`.
- **Duplicado** `(company_id, branch_id, fecha)` → 409 `CONFLICT` (`ConflictException`). Aplica en create y en el estado final del PATCH. En PATCH, la entidad se excluye de la búsqueda de duplicados (igual que el self-exclusion de disponibilidad). La query de duplicado distingue `branch_id IS NULL` vs `branch_id = X` explícitamente, porque Spring Data JPA no genera la condición IS NULL si se pasa null directamente.
- No se valida si la fecha es pasada.

## Fuera de alcance

- Proyección de feriados recurrentes a otros años (decisión #1).
- Notificación o advertencia al crear un turno en feriado — eso es BE-2.3 (Schedule), que consumirá esta tabla.
- `GET /holidays/{id}` — no está en el contrato.
- Scoping de feriados específicos en el cálculo de nómina — BE-3.x.

## Testing

`HolidayControllerTest` (patrón dos-empresas de Fase 1, `@SpringBootTest` + MockMvc + `@Transactional`):

- CRUD feliz: ADMIN crea, lista (verifica orden por fecha), edita parcial, borra.
- ADMIN crea global (sin branchId) → 201; crea específico (con branchId) → 201.
- RRHH lista → 200; RRHH intenta POST → 403.
- SUPERVISOR ve global + feriado de su sucursal; no ve feriado de otra sucursal.
- SUPERVISOR con `?branchId=` de su sucursal → 200 (devuelve globales + los de esa sucursal).
- SUPERVISOR con `?branchId=` de sucursal fuera de su alcance → 404.
- EMPLOYEE intenta GET → 403.
- Duplicado global mismo día → 409; duplicado mismo branch mismo día → 409; global + específico mismo día → 201.
- `branchId` inexistente en create → 404.
- Cross-tenant: feriado de otra empresa → 404; branchId de otra empresa → 404.
- `?anio=` filtra correctamente — feriado de otro año no aparece.
- PATCH self-exclusion: editar un feriado sin cambiar fecha → no se reporta duplicado consigo mismo.
