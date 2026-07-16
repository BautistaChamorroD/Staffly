# FE-1.5 — `features/branches`

**Issue**: [#17](https://github.com/BautistaChamorroD/Staffly/issues/17), rama `feature/branches-screen`. Depende de FE-1.2 (mergeado), FE-1.3 (mergeado) y BE-1.4 (mergeado).

## Contexto

Segunda feature real del frontend, siguiendo el mismo patrón de FE-1.4 (companies): modelos + servicio + form reactivo reusado en alta/edición + smart container con tabla y modales. A diferencia de companies, acá hay control de acceso **dentro** de la pantalla, no solo a nivel de ruta: `ADMIN`, `RRHH` y `SUPERVISOR` pueden ver el listado, pero crear/editar/cambiar estado es exclusivo de `ADMIN` (refleja el `@PreAuthorize` real del backend). No hay modal de revelado único esta vez — `Branch` no genera ningún secreto.

Decisión tomada con el usuario: `core/home/` **no** redirige a `ADMIN`/`RRHH`/`SUPERVISOR` hacia `/branches` en este issue — branches sola no es un buen "home" para esos roles, esa decisión se toma cuando exista también `/employees` (FE-1.6).

## Contrato del backend (`backend/src/main/java/com/staffly/backend/branch/`)

Prefijo `/api/v1/branches`, scoped automáticamente al `company_id` del JWT (el cliente nunca lo manda):

| Método | Path | Roles | Request | Response |
|---|---|---|---|---|
| GET | `/branches` | ADMIN, RRHH, SUPERVISOR | — | `BranchResponse[]` (Supervisor: filtrado por el backend a sus sucursales asignadas — el frontend no replica ese filtro) |
| GET | `/branches/{id}` | ADMIN, RRHH, SUPERVISOR | — | `BranchResponse` |
| POST | `/branches` | ADMIN | `CreateBranchRequest` | `201` `BranchResponse` |
| PATCH | `/branches/{id}` | ADMIN | `UpdateBranchRequest` | `BranchResponse` |
| PATCH | `/branches/{id}/status` | ADMIN | `UpdateBranchStatusRequest` | `BranchResponse` |

```java
record BranchResponse(UUID id, String nombre, String direccion, String zonaHoraria,
                       EstadoSucursal estado, LocalTime horarioVisibleInicio, LocalTime horarioVisibleFin)

enum EstadoSucursal { ACTIVA, INACTIVA }

record CreateBranchRequest(@NotBlank String nombre, @NotBlank String direccion, @NotBlank String zonaHoraria,
                            LocalTime horarioVisibleInicio, LocalTime horarioVisibleFin)

// Actualización parcial: nulls se dejan sin tocar. Sin estado (va por /status).
record UpdateBranchRequest(String nombre, String direccion, String zonaHoraria,
                            LocalTime horarioVisibleInicio, LocalTime horarioVisibleFin)

record UpdateBranchStatusRequest(@NotNull EstadoSucursal estado)
```

`horarioVisibleInicio`/`horarioVisibleFin` son opcionales (pueden ser `null`) — según el roadmap, solo afectan la visualización del armado de horarios en Fase 2, no restringen nada todavía. Se serializan/deserializan como `LocalTime` vía Jackson `JavaTimeModule` (formato ISO, `HH:mm[:ss]` — el input nativo `type="time"` del navegador emite `HH:mm`, compatible con el parser lenient de `DateTimeFormatter.ISO_LOCAL_TIME`). Se verifica el formato real contra el backend en la verificación manual del plan; si Jackson lo rechaza, el fix es acotado a `branch-form` (agregar `:00` antes de enviar).

## Control de acceso dentro de la pantalla (no solo de ruta)

`BranchesListComponent` lee `AuthService.getRole()` una vez al construirse. El botón "+ Nueva sucursal" y las acciones "Editar"/"Activar"/"Desactivar" por fila solo se renderizan (`@if`) cuando el rol es `ADMIN`. RRHH y Supervisor ven la tabla completa en modo solo lectura — coherente con que esos roles no tienen los endpoints de escritura habilitados en el backend; no depender únicamente de que el backend devuelva 403, ocultar la acción de entrada (mismo principio que `frontend/CLAUDE.md` pide para datos de otros empleados, aplicado acá a acciones de UI).

## Ruteo

Nueva ruta `/branches` en `app.routes.ts`, `canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR'])]`, lazy vía `loadComponent`. `core/home/home.component.ts` **no se toca** en este issue (decisión explícita arriba).

## Estructura de archivos

```
frontend/src/app/features/branches/
├── models/
│   └── branch.ts                  → Branch, EstadoSucursal, CreateBranchRequest, UpdateBranchRequest,
│                                      UpdateBranchStatusRequest (reflejan los DTOs de arriba 1:1)
├── services/
│   └── branch.service.ts          → list(), create(), update(), updateStatus() vía HttpClient
└── components/
    ├── branches-list/
    │   ├── branches-list.component.ts/.html/.spec.ts   → smart: fetch inicial, estado de qué modal está
    │   │                                                   abierto, gating de rol, arma la tabla con ui-table
    │   │                                                   + ui-badge por estado
    └── branch-form/
        ├── branch-form.component.ts/.html/.spec.ts       → form reactivo reusado en alta y edición
                                                              (mismos 5 campos en los dos modos — a
                                                              diferencia de company-form no hay campo
                                                              exclusivo de un modo), @Input() mode:
                                                              'create' | 'edit' (solo cambia el texto del
                                                              botón y el título del modal), @Input()
                                                              initialValue?: Branch, @Output() submitted
```

El modal de confirmación de activar/desactivar vive directo en `branches-list`, mismo patrón que `companies-list`.

## Interacción — `branches-list`

- Al entrar: `branch.service.list()`, estado de carga; si falla, banner de error.
- Tabla vacía: mensaje simple, sin diseño especial.
- (Solo `ADMIN`) Botón "+ Nueva sucursal" → `ui-modal` con `branch-form` en modo `create`. Al emitir `submitted`, `branch.service.create()`; si OK, cierra y refresca; si falla, banner de error dentro del modal (no lo cierra).
- (Solo `ADMIN`) "Editar" por fila → `ui-modal` con `branch-form` en modo `edit`, precargado. Igual patrón de éxito/error.
- (Solo `ADMIN`) "Activar"/"Desactivar" por fila (label según estado actual) → `ui-modal` de confirmación simple. Al confirmar, `branch.service.updateStatus()`; igual patrón de éxito/error.

## Fuera de alcance

- Réplica en frontend del filtro de sucursales asignadas a un Supervisor — ya lo hace el backend, el frontend solo renderiza lo que la API devuelve.
- Cualquier uso de `horarioVisibleInicio/Fin` más allá de cargarlos/mostrarlos — el armado de horarios que los consume de verdad es Fase 2 (FE-2.2a).
- Redirect de `home` para ADMIN/RRHH/SUPERVISOR — decisión explícita de dejarlo para cuando exista FE-1.6.

## Testing

- `branch.service.spec.ts`: los 4 métodos contra `provideHttpClientTesting()`/`HttpTestingController`, verificando método HTTP, URL y body.
- `branch-form.component.spec.ts`: validación de campos requeridos (nombre/dirección/zonaHoraria), que `horarioVisibleInicio/Fin` son opcionales, que precarga desde `initialValue`, que `submitted` emite el valor correcto.
- `branches-list.component.spec.ts`: carga inicial (loading → datos), error de carga, gating de rol (con `AuthService` mockeado: `ADMIN` ve botón+acciones, `RRHH`/`SUPERVISOR` no), flujo de alta, edición y activar/desactivar — contra un `BranchService` mockeado, verificando comportamiento observable.
- Verificación manual: mismo mecanismo que FE-1.4 (backend real, dev profile, datos insertados vía `/h2-console`), esta vez además probando con un usuario `RRHH` o `SUPERVISOR` para confirmar que las acciones de escritura no aparecen.
