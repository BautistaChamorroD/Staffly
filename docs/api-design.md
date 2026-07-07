# Staffly — Diseño de API REST (v1.0)

Complementa el documento de requerimientos (`requerimientos-sistema-gestion-personal.md`) y el ERD (`erd-staffly.md`). Define el contrato de la API que consume el frontend y que implementa el backend.

---

## Convenciones generales

### Base URL y versionado
```
/api/v1/...
```

### Autenticación
Todos los endpoints requieren header `Authorization: Bearer <access_token>`, excepto `/auth/login` y `/auth/refresh`. El `company_id` y `rol` del usuario se derivan **siempre** del JWT — ningún endpoint acepta `companyId` como parámetro de entrada (regla dura, ver sección 7 del documento de requerimientos).

### Formato de fecha/hora
ISO 8601 (`2026-07-06T14:30:00Z` para timestamps, `2026-07-06` para fechas sin hora).

### Paginación
Endpoints de listado aceptan `?page=0&size=20&sort=campo,asc`. Respuesta:
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8
}
```

### Formato estándar de error
```json
{
  "code": "EMPLOYEE_NOT_FOUND",
  "message": "No se encontró el empleado solicitado",
  "details": null,
  "timestamp": "2026-07-06T14:30:00Z"
}
```

### Códigos de estado HTTP usados
| Código | Uso |
|---|---|
| 200 | Operación exitosa (GET, PUT, PATCH) |
| 201 | Recurso creado (POST) |
| 204 | Operación exitosa sin contenido de respuesta (DELETE lógico) |
| 400 | Error de validación de datos de entrada |
| 401 | No autenticado / token inválido o expirado |
| 403 | Autenticado pero sin permisos para esta acción (rol/alcance insuficiente) |
| 404 | Recurso no encontrado (o perteneciente a otro tenant — ver nota de seguridad) |
| 409 | Conflicto (ej. solapamiento de turnos, período ya cerrado) |
| 422 | Regla de negocio violada (ej. baja con reglas específicas, ver casos puntuales abajo) |

**Nota de seguridad importante**: si un recurso existe pero pertenece a otra `Company`, la API responde **404** (nunca 403) — no se debe revelar la existencia de datos de otro tenant, ni siquiera con un código que la insinúe.

---

## 1. Autenticación (`/auth`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| POST | `/auth/login` | Login con email + password. Devuelve access token + refresh token. | Público |
| POST | `/auth/refresh` | Renueva el access token a partir de un refresh token válido. | Público (requiere refresh token) |
| POST | `/auth/logout` | Invalida el refresh token actual. | Autenticado |
| POST | `/auth/change-password` | Cambia la contraseña del usuario autenticado (usado también para el cambio forzado en primer login, ver RF-01). | Autenticado |

**POST /auth/login** — request:
```json
{ "email": "admin@heladeria.com", "password": "..." }
```
Response 200:
```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "expiresIn": 1800,
  "user": { "id": "...", "email": "...", "rol": "ADMIN", "debeCambiarPassword": false }
}
```

---

## 2. Empresas (`/companies`) — solo Super Admin

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/companies` | Lista todas las empresas (tenants). | SUPER_ADMIN |
| POST | `/companies` | Crea una empresa nueva + primer usuario Admin (RF-01). | SUPER_ADMIN |
| GET | `/companies/{id}` | Detalle de una empresa. | SUPER_ADMIN |
| PATCH | `/companies/{id}` | Edita datos de una empresa (nombre, país, moneda, etc.). | SUPER_ADMIN |
| PATCH | `/companies/{id}/status` | Suspende o reactiva una empresa. | SUPER_ADMIN |

**POST /companies** — request:
```json
{
  "nombre": "Heladería Don José",
  "pais": "AR",
  "moneda": "ARS",
  "zonaHoraria": "America/Argentina/Buenos_Aires",
  "adminEmail": "admin@heladeria.com"
}
```
Response 201 incluye la empresa creada y confirma el envío/generación de contraseña provisoria para el Admin.

---

## 3. Sucursales (`/branches`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/branches` | Lista sucursales de la empresa del usuario autenticado. | ADMIN, RRHH, SUPERVISOR (solo las suyas) |
| POST | `/branches` | Crea una sucursal. | ADMIN |
| GET | `/branches/{id}` | Detalle de una sucursal. | ADMIN, RRHH, SUPERVISOR (si es suya) |
| PATCH | `/branches/{id}` | Edita una sucursal. | ADMIN |
| PATCH | `/branches/{id}/status` | Activa/desactiva una sucursal. | ADMIN |

---

## 4. Usuarios (`/users`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/users` | Lista usuarios de la empresa. Filtros: `?rol=`, `?estado=`. | ADMIN |
| POST | `/users` | Crea un usuario (ej. da acceso a un Employee existente, o crea un RRHH/Supervisor). | ADMIN |
| GET | `/users/{id}` | Detalle de un usuario. | ADMIN, o el propio usuario (`/users/me`) |
| GET | `/users/me` | Datos del usuario autenticado. | Autenticado |
| PATCH | `/users/{id}` | Edita rol, sucursales asignadas (si Supervisor), estado. | ADMIN |
| PATCH | `/users/{id}/status` | Activa/desactiva un usuario. | ADMIN |

---

## 5. Empleados (`/employees`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/employees` | Lista empleados. Filtros: `?estadoLaboral=`, `?branchId=`, `?search=` (nombre/documento). | ADMIN, RRHH, SUPERVISOR (solo de sus sucursales) |
| POST | `/employees` | Alta de empleado (RF-04). | ADMIN, RRHH |
| GET | `/employees/{id}` | Detalle de un empleado. | ADMIN, RRHH, SUPERVISOR (si es de su sucursal), el propio EMPLOYEE (`/employees/me`) |
| GET | `/employees/me` | Datos del empleado autenticado. | EMPLOYEE |
| PATCH | `/employees/{id}` | Edita datos del empleado (RF-06: cambios quedan en `AuditLog`). | ADMIN, RRHH |
| PATCH | `/employees/{id}/status` | Cambia `estadoLaboral` (ej. a `BAJA`). No bloquea si tiene `estadoLiquidacion = PENDIENTE` (RF-07b). | ADMIN, RRHH |
| GET | `/employees/{id}/history` | Historial de cambios de puesto/categoría/sueldo (RF-06). | ADMIN, RRHH |

**Nota de alcance (RF-29)**: `/employees/{id}` para un usuario EMPLOYEE devuelve **403** si `{id}` no coincide con su propio registro — nunca se expone el dato de otro empleado ni con 404 ni con 403 filtrando campos, directamente no se procesa la request.

---

## 6. Disponibilidad (`/employees/{employeeId}/availability`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/employees/{employeeId}/availability` | Lista la disponibilidad declarada del empleado. | ADMIN, RRHH, SUPERVISOR, el propio EMPLOYEE |
| POST | `/employees/{employeeId}/availability` | Carga una franja de disponibilidad. Sin aprobación, válida al crearse (RF-08). | ADMIN, RRHH, el propio EMPLOYEE |
| PATCH | `/employees/{employeeId}/availability/{id}` | Edita una franja. | ADMIN, RRHH, el propio EMPLOYEE |
| DELETE | `/employees/{employeeId}/availability/{id}` | Elimina una franja. | ADMIN, RRHH, el propio EMPLOYEE |

---

## 7. Horarios / turnos (`/schedules`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/schedules` | Lista turnos. Filtros: `?employeeId=`, `?branchId=`, `?desde=`, `?hasta=`. | ADMIN, RRHH, SUPERVISOR (sus sucursales), EMPLOYEE (solo los suyos) |
| POST | `/schedules` | Crea un turno. Valida solapamiento (RF-15, entre cualquier sucursal) → **409** si hay conflicto. Si está fuera de la disponibilidad declarada, responde **201** igual pero incluye `"warning": "OUT_OF_AVAILABILITY"` en el body (RF-10). | ADMIN, RRHH, SUPERVISOR |
| GET | `/schedules/{id}` | Detalle de un turno. | ADMIN, RRHH, SUPERVISOR, EMPLOYEE (si es suyo) |
| PATCH | `/schedules/{id}` | Edita un turno (mismas validaciones que POST). | ADMIN, RRHH, SUPERVISOR |
| DELETE | `/schedules/{id}` | Elimina un turno planificado. | ADMIN, RRHH, SUPERVISOR |
| POST | `/schedules/{id}/confirm` | Marca el turno como `CONFIRMADO`. | ADMIN, RRHH, SUPERVISOR |
| PATCH | `/schedules/{id}/status` | Cambia estado (`CUMPLIDO`/`AUSENTE`). | ADMIN, RRHH, SUPERVISOR |
| POST | `/schedules/{id}/duplicate-weekly` | Duplica un turno como plantilla fija semanal (soporte a horario fijo, ver RF-11). | ADMIN, RRHH, SUPERVISOR |

**POST /schedules** — request:
```json
{
  "employeeId": "...",
  "branchId": "...",
  "fechaHoraInicio": "2026-07-10T22:00:00",
  "fechaHoraFin": "2026-07-11T06:00:00",
  "tipoTurno": "ROTATIVO"
}
```

---

## 8. Feriados (`/holidays`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/holidays` | Lista feriados de la empresa. Filtros: `?branchId=`, `?anio=`. | ADMIN, RRHH, SUPERVISOR |
| POST | `/holidays` | Crea un feriado (opcionalmente asociado a una sucursal). | ADMIN |
| PATCH | `/holidays/{id}` | Edita un feriado. | ADMIN |
| DELETE | `/holidays/{id}` | Elimina un feriado. | ADMIN |

---

## 9. Licencias (`/leave-types`, `/leave-requests`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/leave-types` | Lista tipos de licencia configurados (RF-15b). | ADMIN, RRHH, SUPERVISOR, EMPLOYEE |
| POST | `/leave-types` | Crea un tipo de licencia. | ADMIN |
| PATCH | `/leave-types/{id}` | Edita un tipo de licencia. | ADMIN |
| GET | `/leave-requests` | Lista solicitudes. Filtros: `?employeeId=`, `?estado=`. | ADMIN, RRHH, SUPERVISOR (sus sucursales), EMPLOYEE (las suyas) |
| POST | `/leave-requests` | Crea una solicitud de licencia (RF-15c), estado inicial `PENDIENTE`. | EMPLOYEE (propia), ADMIN, RRHH (a nombre de un empleado) |
| GET | `/leave-requests/{id}` | Detalle de una solicitud. | ADMIN, RRHH, SUPERVISOR, EMPLOYEE (si es propia) |
| POST | `/leave-requests/{id}/approve` | Aprueba la solicitud (RF-15d). Al aprobarse, bloquea turnos superpuestos (RF-15e) → si ya existe un `Schedule` en ese rango, responde **409** con el conflicto detallado en vez de aprobar silenciosamente. | ADMIN, RRHH, SUPERVISOR |
| POST | `/leave-requests/{id}/reject` | Rechaza la solicitud (requiere `motivo` en el body). | ADMIN, RRHH, SUPERVISOR |
| POST | `/leave-requests/{id}/cancel` | Cancela una solicitud propia (solo si está `PENDIENTE` o `APROBADA` y no comenzó aún). | EMPLOYEE (propia), ADMIN, RRHH |

---

## 10. Configuración de nómina (`/payroll-config`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/payroll-config` | Obtiene la configuración de nómina de la empresa (única por Company). | ADMIN |
| PUT | `/payroll-config` | Actualiza la configuración completa (upsert, dado que es 1 a 1 con Company). | ADMIN |

---

## 11. Períodos de nómina (`/payroll-periods`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/payroll-periods` | Lista períodos. Filtros: `?estado=`. | ADMIN, RRHH |
| POST | `/payroll-periods` | Abre un nuevo período (fecha inicio/fin). | ADMIN, RRHH |
| GET | `/payroll-periods/{id}` | Detalle de un período. | ADMIN, RRHH |
| POST | `/payroll-periods/{id}/close` | Cierra el período: dispara el cálculo de todos los `Payslip` correspondientes (RF-17, RF-20). Responde **409** si ya está cerrado. | ADMIN, RRHH |
| POST | `/payroll-periods/{id}/reopen` | Reabre un período cerrado (operación sensible). | ADMIN (únicamente) |

---

## 12. Adelantos (`/advances`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/advances` | Lista adelantos. Filtros: `?employeeId=`, `?estado=`. | ADMIN, RRHH |
| POST | `/advances` | Registra un adelanto (RF-19). | ADMIN, RRHH |
| GET | `/advances/{id}` | Detalle de un adelanto. | ADMIN, RRHH, EMPLOYEE (si es propio) |
| DELETE | `/advances/{id}` | Elimina un adelanto no descontado aún (por error de carga). | ADMIN, RRHH |

---

## 13. Recibos de sueldo (`/payslips`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/payslips` | Lista recibos. Filtros: `?employeeId=`, `?payrollPeriodId=`, `?estado=`. | ADMIN, RRHH, EMPLOYEE (solo los propios) |
| GET | `/payslips/{id}` | Detalle de un recibo (incluye desglose de conceptos). | ADMIN, RRHH, EMPLOYEE (si es propio) |
| GET | `/payslips/{id}/pdf` | Descarga el recibo en PDF (RF-18, decisión #4 sección 8). | ADMIN, RRHH, EMPLOYEE (si es propio) |
| POST | `/payslips/{id}/void` | Anula un recibo pagado y genera uno nuevo de ajuste vinculado (RF-20b). Requiere `motivo`. | ADMIN (únicamente) |
| PATCH | `/payslips/{id}/mark-paid` | Marca un recibo como pagado (registra fecha de pago). | ADMIN, RRHH |

---

## 14. Reportes (`/reports`)

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/reports/hours-worked` | Horas trabajadas por empleado/sucursal/período (RF-22). Filtros: `?branchId=`, `?desde=`, `?hasta=`. | ADMIN, RRHH |
| GET | `/reports/payroll-cost` | Costo de nómina por sucursal/empresa/período (RF-23). | ADMIN, RRHH |
| GET | `/reports/pending-advances` | Adelantos pendientes (RF-24). | ADMIN, RRHH |
| GET | `/reports/{report}/export` | Exporta el reporte correspondiente. Query param `?format=pdf|csv` (RF-25, PDF prioritario). | ADMIN, RRHH |

---

## 15. Auditoría (`/audit-log`) — solo lectura

| Método | Endpoint | Descripción | Rol requerido |
|---|---|---|---|
| GET | `/audit-log` | Lista cambios auditados. Filtros: `?entidad=`, `?entidadId=`, `?userId=`, `?desde=`, `?hasta=`. | ADMIN |

---

## Resumen de casos 409/422 a tener presentes en la implementación

Estos son los conflictos de negocio más importantes que la API debe modelar explícitamente (no como un 400 genérico):

| Situación | Código | Endpoint afectado |
|---|---|---|
| Turno se superpone con otro turno del mismo empleado (misma o distinta sucursal) | 409 | `POST/PATCH /schedules` |
| Se intenta cerrar un período de nómina ya cerrado | 409 | `POST /payroll-periods/{id}/close` |
| Se intenta aprobar una licencia que se superpone con un turno ya asignado | 409 | `POST /leave-requests/{id}/approve` |
| Se intenta editar directamente un Payslip con estado `pagado` (en vez de usar `/void`) | 422 | `PATCH /payslips/{id}` (este endpoint de edición directa **no existe a propósito** — solo `/void` y `/mark-paid`) |

---

## Próximos pasos sugeridos de diseño técnico (no cubiertos en este documento)

- Esquema exacto de DTOs de request/response por endpoint (hoy están sugeridos con ejemplos, no formalizados campo por campo).
- Definición de permisos granulares si se decide ir más allá de roles fijos (mencionado como posible evolución en la sección 2 del documento de requerimientos).
- Rate limiting y throttling si se expone la API públicamente.
