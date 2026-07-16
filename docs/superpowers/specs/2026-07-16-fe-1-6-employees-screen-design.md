# FE-1.6 — `features/employees`

**Issue**: [#18](https://github.com/BautistaChamorroD/Staffly/issues/18), rama `feature/employees-screen`. Depende de FE-1.5 (mergeado) y BE-1.6 (mergeado). Cierra la Fase 1 del frontend.

## Contexto

Pantalla más grande hasta ahora: filtros server-side, búsqueda con debounce, y un formulario con más campos que `companies`/`branches`, incluyendo dos selects (`tipoContrato`, y el cambio de `estadoLaboral` de 4 valores) y asignación a múltiples sucursales. Ninguno de los 6 componentes del kit de FE-1.3 cubre un `<select>` — se confirmó con el usuario agregar `ui-select` al kit (mismo criterio ya usado para extender `ui-input` con `autocomplete` en la migración del login).

## Contrato del backend (`backend/src/main/java/com/staffly/backend/employee/`)

Prefijo `/api/v1/employees`, scoped automáticamente al `company_id` del JWT:

| Método | Path | Roles | Query params | Request | Response |
|---|---|---|---|---|---|
| GET | `/employees` | ADMIN, RRHH, SUPERVISOR | `estadoLaboral?`, `branchId?`, `search?` (todos opcionales) | — | `EmployeeResponse[]` |
| GET | `/employees/{id}` | ADMIN, RRHH, SUPERVISOR | — | — | `EmployeeResponse` |
| POST | `/employees` | ADMIN, RRHH | — | `CreateEmployeeRequest` | `201` `EmployeeResponse` |
| PATCH | `/employees/{id}` | ADMIN, RRHH | — | `UpdateEmployeeRequest` | `EmployeeResponse` |
| PATCH | `/employees/{id}/status` | ADMIN, RRHH | — | `UpdateEmployeeStatusRequest` | `EmployeeResponse` |

`GET /employees/me` (rol `EMPLOYEE`) y `GET /employees/{id}/history` (auditoría) existen en el backend pero están fuera de alcance de este issue — el primero es Fase 2+ (pantalla propia del empleado), el segundo depende de `AuditLog` completo (Fase 5).

```java
record EmployeeResponse(UUID id, String nombre, String apellido, String documento, LocalDate fechaNacimiento,
                         LocalDate fechaIngreso, LocalDate fechaEgreso, TipoContrato tipoContrato,
                         String categoria, BigDecimal sueldoBase, String telefono, String emailContacto,
                         EstadoLaboral estadoLaboral, EstadoLiquidacion estadoLiquidacion, List<UUID> branchIds)

enum EstadoLaboral { ACTIVO, LICENCIA, SUSPENDIDO, BAJA }
enum EstadoLiquidacion { AL_DIA, PENDIENTE }   // solo lectura, lo maneja nómina (Fase 3)
enum TipoContrato { JORNADA_COMPLETA, JORNADA_PARCIAL, POR_HORA }

record CreateEmployeeRequest(@NotBlank String nombre, @NotBlank String apellido, @NotBlank String documento,
                              @NotNull LocalDate fechaNacimiento, @NotNull LocalDate fechaIngreso,
                              LocalDate fechaEgreso, @NotNull TipoContrato tipoContrato,
                              @NotBlank String categoria, @NotNull BigDecimal sueldoBase,
                              String telefono, String emailContacto, @NotEmpty List<UUID> branchIds)

// Actualización parcial: nulls se dejan sin tocar. Sin estadoLaboral (va por /status) ni estadoLiquidacion
// (no editable por API, lo maneja nómina).
record UpdateEmployeeRequest(String nombre, String apellido, String documento, LocalDate fechaNacimiento,
                              LocalDate fechaIngreso, LocalDate fechaEgreso, TipoContrato tipoContrato,
                              String categoria, BigDecimal sueldoBase, String telefono, String emailContacto,
                              List<UUID> branchIds)

record UpdateEmployeeStatusRequest(@NotNull EstadoLaboral estadoLaboral)
```

`estado_laboral` y `estado_liquidacion` son independientes entre sí (un empleado puede estar `BAJA` + `PENDIENTE` simultáneamente) — la tabla los muestra en columnas separadas, nunca combinados (`ux-decisions.md` #3). La baja (RF-07b) no se bloquea por saldos pendientes — es un cambio de estado normal, no una operación especial.

## Nuevo componente del kit: `ui-select`

`frontend/src/app/shared/components/select/` — mismo patrón que `ui-input` (Task de FE-1.3): implementa `ControlValueAccessor`, se usa con `formControlName` directo.

- `@Input() label: string`
- `@Input() options: { value: string; label: string }[]`
- `@Input() placeholder?: string` — se renderiza como primer `<option>` deshabilitado si está seteado
- `@Input() errorMessage?: string`

Además, se agrega `@Input() step?: string` a `ui-input` (ya existente), enlazado vía `[attr.step]` en el `<input>` nativo — lo necesita `sueldoBase` (`type="number"` sin `step` no maneja bien los decimales del navegador).

## Estructura de archivos

```
frontend/src/app/shared/components/select/
├── select.component.ts/.html/.spec.ts

frontend/src/app/features/employees/
├── models/
│   └── employee.ts                  → Employee, EstadoLaboral, EstadoLiquidacion, TipoContrato,
│                                        CreateEmployeeRequest, UpdateEmployeeRequest,
│                                        UpdateEmployeeStatusRequest, EmployeeListFilters
├── services/
│   └── employee.service.ts          → list(filters), create(), update(), updateStatus()
└── components/
    ├── employee-form/               → reusado en alta y edición
    └── employees-list/              → smart: filtros (search debounced + 2 selects), tabla,
                                         modal de alta/edición, modal de cambio de estado laboral
```

## Interacción — `employees-list`

- **Filtros**: `FormGroup` con `search` (texto), `estadoLaboral` (`ui-select`, opción "Todos"), `branchId` (`ui-select`, opción "Todas", poblado desde `BranchService.list()`, ya existente de FE-1.5). `search.valueChanges` con `debounceTime(300)` + `distinctUntilChanged()`; los tres controles disparan un nuevo `employeeService.list(filters)` — todo el filtrado es server-side, vía los query params que el backend ya soporta.
- **Tabla**: Nombre (`nombre apellido`), Sucursal(es) (nombres resueltos contra la lista de `Branch` ya cargada para el filtro — un empleado puede tener más de una), Puesto (`categoria`), Estado laboral (`ui-badge`: `ACTIVO`→success, `LICENCIA`→accent, `SUSPENDIDO`→warning, `BAJA`→neutral), Estado liquidación (`ui-badge`, solo lectura: `AL_DIA`→success, `PENDIENTE`→warning), acciones "Editar" y "Cambiar estado" (ambas visibles para `ADMIN` y `RRHH` — a diferencia de `branches`, acá no hay gating de rol dentro de la pantalla porque los tres roles con acceso a la ruta ya tienen los mismos permisos de escritura según el backend, salvo `SUPERVISOR`, que según el `@PreAuthorize` de arriba **no** tiene `POST`/`PATCH` habilitados — mismo patrón de `branches`: `isAdmin` se generaliza a `canWrite = rol === 'ADMIN' || rol === 'RRHH'`, oculta alta/editar/cambiar-estado para `SUPERVISOR`).
- **Alta/edición**: `ui-modal` con `employee-form`. Al emitir `submitted`, `create()`/`update()`; éxito cierra y refresca; error, banner dentro del modal.
- **Cambio de estado laboral**: "Cambiar estado" por fila abre `ui-modal` con un `ui-select` precargado al `estadoLaboral` actual del empleado + botones Guardar/Cancelar (mismo patrón de confirmación que `companies`/`branches`, generalizado de un toggle binario a un select de 4 valores). Al guardar, `updateStatus()`; éxito cierra y refresca; error, banner dentro del modal.

## Ruteo

Nueva ruta `/employees` en `app.routes.ts`, `canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR'])]`, lazy vía `loadComponent` — mismos roles que pueden leer el listado (`SUPERVISOR` incluido, aunque sin escritura, igual que en `branches`).

**Se resuelve la decisión diferida de FE-1.5**: `core/home/home.component.ts` ahora redirige también a `ADMIN`, `RRHH` y `SUPERVISOR` — a `/employees`, no a `/branches` (es la pantalla de uso más frecuente del día a día para esos roles). Queda: `SUPER_ADMIN` → `/companies`, `ADMIN`/`RRHH`/`SUPERVISOR` → `/employees`, `EMPLOYEE` sigue viendo el placeholder (no tiene pantalla propia todavía).

## Fuera de alcance

- `GET /employees/me` — pantalla propia del empleado, no es parte de este issue (rol `EMPLOYEE` no tiene ruta a `/employees` de todos modos).
- `GET /employees/{id}/history` — depende de `AuditLog` completo, Fase 5.
- Carga de documentación asociada (RF-07, "opcional, a definir formato/almacenamiento") — no está modelado en el backend todavía.
- Foto del empleado — mencionada como opcional en el documento de requerimientos, no está en `EmployeeResponse` del backend actual.
- Multi-select "elegante" para `branchIds` — lista de checkboxes alcanza hoy (un solo cliente real, una sola sucursal).

## Testing

- `select.component.spec.ts` (Task del kit): reflection de valor vía `ControlValueAccessor`, renderiza las `options` pasadas, el `placeholder` aparece como primera opción deshabilitada, `errorMessage` se muestra igual que en `ui-input`.
- `employee.service.spec.ts`: los 4 métodos, incluyendo que `list()` arma los `HttpParams` correctamente cuando se pasan filtros y cuando no se pasa ninguno (sin query string).
- `employee-form.component.spec.ts`: validación de campos requeridos, que `branchIds` se arma correctamente desde los checkboxes tildados, que precarga desde `initialValue` (incluida la selección de sucursales), que `submitted` emite el valor correcto.
- `employees-list.component.spec.ts`: carga inicial, error de carga, que cambiar cualquiera de los 3 filtros dispara un nuevo `list()` con los parámetros correctos (incluido el debounce del buscador — usar los fake timers de Vitest), gating de escritura para `SUPERVISOR`, flujo de alta/edición, flujo de cambio de estado laboral con el modal de 4 opciones.
- Verificación manual: mismo mecanismo que FE-1.5 (backend real, dev profile, datos insertados vía `/h2-console`) — esta vez con al menos 2 sucursales y 2-3 empleados de prueba en estados distintos, para poder probar filtros/búsqueda/badges con datos reales, y un usuario `SUPERVISOR` para confirmar el gating de escritura.
