# Staffly — Roadmap de implementación (Issues → Branches → PRs)

Basado en `requerimientos-sistema-gestion-personal.md` (sección 9), `erd-staffly.md`, `api-design.md`, `patrones-diseno.md`, `flujos-proceso.md`, `ux-decisions.md` y los `CLAUDE.md` de `backend/` y `frontend/`.

---

## 0. Supuestos y criterio (leer antes de arrancar)

**Corrección sobre el punto de partida.** El `CLAUDE.md` de backend sugiere que el modelo base de Fase 1 ya está scaffoldeado, pero en la realidad de tu repo hoy solo existe la división de carpetas `backend/`/`frontend/`, sin nada modelado adentro — ni `TenantAwareEntity`, ni `Company`, `Branch`, `Employee` o `User` como entidades JPA. Ajusté el roadmap: la Fase 1 ahora arranca con el modelado real de esas entidades (BE-1.0 y BE-1.0b más abajo) antes de tocar seguridad o CRUDs. El resto del documento no cambia de lógica, solo se corrió la base de partida.

**Un solo repo, dos carpetas — no dos repos.** Todo vive en un único repositorio, con `backend/` y `frontend/` en la raíz, cada una con su propio `CLAUDE.md`. Los prefijos `BE-`/`FE-` en los IDs de este documento son solo una convención mía para que se vea de un vistazo si un issue toca backend o frontend — no implican repos separados. Todas las ramas salen del mismo `main` y todos los PRs van hacia ese mismo `main`. Justamente por ser un solo repo, las labels `backend`/`frontend` que arma la Fase 0 pasan a ser importantes de verdad: son las que te van a dejar filtrar el tablero de issues por área cuando estén todos mezclados en el mismo tracker. Las rutas de "Archivos/paquetes" de cada issue ya están escritas con el prefijo `backend/` o `frontend/` correspondiente — `backend/X` equivale a `backend/src/main/java/com/staffly/backend/X`, y `frontend/X` a `frontend/src/app/X`.

**Nivel de detalle: lo mantuve, con algunos ajustes.** Pediste que te diga si es excesivo. Mi lectura: para trabajar con Claude Code, 1 issue ≈ 1 sesión/prompt con alcance claro es el tamaño correcto — más grueso ("hacer todo el módulo de nómina") es difícil de acotarle a un agente en una sola pasada, más fino ("crear el DTO de Employee") genera fricción de PRs sin aportar checkpoints útiles. Por eso agrupé cada módulo backend (entidad + repo + service + controller + tests) en **un** issue, y solo separé en dos donde la UX/lógica es genuinamente compleja (el armado de horarios, línea de tiempo continua; el cálculo de liquidación), o donde el modelado de datos merece su propio checkpoint antes de construir lógica encima (el modelo base de Fase 1). Resultado: **57 issues** (36 backend, 21 frontend) a lo largo de 5 fases. Es un volumen real para un SaaS v1 completo — si a mitad de camino sentís que es mucho, lo recortamos fase por fase, no hace falta decidirlo todo ahora.

**Una desviación menor que marco explícitamente:** el `CLAUDE.md` de backend ubica `common/audit` completo en la Fase 5. Pero RF-06 (historial de cambios de empleado) y RF-10 (registro de asignación fuera de disponibilidad) necesitan que algo se esté auditando desde la Fase 1-2 para tener datos reales cuando lleguemos a Fase 5. Dejé el módulo completo (consulta, filtros, pantalla) en Fase 5 tal cual el orden sugerido, pero marqué como **opcional** un registro mínimo antes (ver BE-1.8). Si preferís no adelantar nada y aceptar que el historial "arranca vacío" en Fase 5, salteá ese issue sin problema.

**Explícitamente fuera de este roadmap** (fuera de alcance v1 según el documento de requerimientos, sección 1.2/10): fichaje/asistencia biométrica, integraciones AFIP/ARCA/gobierno, billing del propio SaaS, notificaciones por email, app móvil nativa. No aparecen como issues en ningún lado de este documento.

**Cadencia asumida: part-time, ~6-8 horas por semana** (2-3 sesiones cortas), con vos usando Claude Code para la implementación y revisando/mergeando los PRs. A ese ritmo, un issue de modelado o CRUD simple ronda 1-1.5h con asistencia de IA, uno de UI simple 1.5-2h, y los complejos (armado de horarios, cálculo de liquidación, cierre de período) entre 3 y 5h. Con eso, el roadmap completo son **~24 semanas (≈5-6 meses)**. Si tu ritmo real es distinto, es fácil recalcular: la cantidad de issues no cambia, solo cuántos entran por semana.

### Cómo usar el checklist

Cada ítem es `- [ ] ID — Título (rama)`. Tachalo (`- [x]`) recién cuando el PR esté mergeado a `main`, no cuando lo abrís — así el checklist refleja "hecho" de verdad. Para el estado intermedio "en progreso", te sugiero abrir el Issue en GitHub y ponerle una label `en-progreso` (o mover la card en un Project board) en vez de un tercer estado acá, que un checkbox de Markdown no soporta nativamente.

---

## 1. Resumen ejecutivo

| Fase | Contenido | Issues BE | Issues FE | Semanas (tentativas) |
|---|---|---|---|---|
| Fase 0 | Setup del repo y flujo de trabajo | — | — | Semana 1 (en paralelo) |
| Fase 1 | Core multi-tenant (modelado + Company/Branch/Employee/User, auth, roles) | 11 | 6 | 1 – 6 |
| Fase 2 | Disponibilidad, horarios, licencias | 6 | 4 | 7 – 11 |
| Fase 3 | Nómina (config, cálculo, adelantos, recibos) | 11 | 5 | 12 – 18 |
| Fase 4 | Reportes | 4 | 1 | 19 – 20 |
| Fase 5 | Auditoría, i18n, pulido, performance | 4 | 5 | 21 – 24 |
| **Total** | | **36** | **21** | **~24 semanas** |

---

## Fase 0 — Setup del repo y flujo de trabajo

No son issues de implementación, es configuración de una sola vez para que el resto del roadmap funcione como vos lo pediste (issues → branches → PRs).

- [ ] Activar branch protection en `main` (require PR, no push directo).
- [ ] Agregar PR template básico (`.github/pull_request_template.md`): qué cambia, cómo probarlo, issue relacionado.
- [ ] Crear labels: `fase-1` … `fase-5`, `backend`, `frontend`, `en-progreso`, `bloqueado`.
- [ ] (Opcional) Crear un GitHub Project con columnas Pendiente / En progreso / Hecho y cargar los 57 issues de este documento.

---

## Fase 1 — Core multi-tenant

### Backend (`backend/`)

**Semana 1**

- [ ] **BE-1.0** — Bootstrap del proyecto + PostgreSQL local + infraestructura multi-tenant + entidad `Company` (`feature/project-bootstrap-tenant-company`)
  - Qué implica: dependencias base en `pom.xml` (Spring Web, Data JPA, Security, Flyway, H2/PostgreSQL, springdoc-openapi, `jjwt`), `docker-compose.yml` con un servicio de PostgreSQL para levantar la base real en local (no solo H2), `application.yml` con perfiles `dev` (H2 en memoria, para tests rápidos), `postgres-local` (contra el Postgres del docker-compose) y `prod`, `TenantAwareEntity` (paquete `tenant/`) con `@FilterDef`/`@Filter` `tenantFilter` (la **definición** del filtro, sin activarlo todavía — eso depende del JWT y se hace en BE-1.1), y la entidad `Company` (JPA) + primera migración Flyway, corrida contra Postgres real vía `docker compose up` para confirmar que el esquema se crea bien ahí y no solo en H2. Es el primer commit real de código del backend, y deja la base de datos lista antes de construir nada encima.
  - Archivos/paquetes: `backend/` (`pom.xml`, `application.yml`, `docker-compose.yml`), `backend/tenant/`, `backend/company/`
  - Depende de: nada

- [ ] **BE-1.0b** — Entidades `Branch`, `Employee`, `User` (`feature/core-entities-branch-employee-user`)
  - Qué implica: las 3 entidades restantes del modelo base de Fase 1, extendiendo `TenantAwareEntity`, con sus relaciones — `Employee` asignado a 1..N `Branch`, `User` con relación opcional 1 a 1 con `Employee` — más las migraciones Flyway correspondientes, corridas contra el Postgres local del `docker-compose.yml` de BE-1.0 (no solo H2). Solo el modelo persistido (entidad + enum de estado si aplica), sin lógica de negocio ni endpoints todavía.
  - Archivos/paquetes: `backend/branch/`, `backend/employee/`, `backend/user/`
  - Depende de: BE-1.0

- [ ] **BE-1.1** — Seguridad JWT + activación del filtro multi-tenant (`feature/security-jwt-tenant-filter`)
  - Qué implica: Spring Security + `jjwt`, `JwtService`, `StafflyUserPrincipal`, y `TenantFilterInterceptor` que activa el `tenantFilter` de Hibernate (definido en BE-1.0) con el `company_id` tomado del JWT. Sin esto, ningún endpoint protegido puede funcionar — es la base de todo lo demás.
  - Archivos/paquetes: `backend/security/`, `backend/tenant/`
  - Depende de: BE-1.0b

**Semana 2**

- [ ] **BE-1.2** — Endpoints de `/auth` (`feature/auth-endpoints`)
  - Qué implica: `login`, `refresh`, `logout`, `change-password`. Login valida contra BCrypt y emite access+refresh token; `change-password` cubre también el cambio forzado del primer login (RF-01, `debe_cambiar_password`).
  - Archivos/paquetes: `backend/security/`, `backend/user/`
  - Depende de: BE-1.1

- [ ] **BE-1.3** — CRUD `Company` + alta con Admin inicial (`feature/company-crud`)
  - Qué implica: `GET/POST/PATCH /companies`, `PATCH /companies/{id}/status`, solo Super Admin. El alta de una `Company` crea además el primer `User` rol `ADMIN` con contraseña provisoria (RF-01).
  - Archivos/paquetes: `backend/company/`, `backend/user/`
  - Depende de: BE-1.1, BE-1.0

- [ ] **BE-1.4** — CRUD `Branch` (`feature/branch-crud`)
  - Qué implica: `GET/POST/PATCH /branches`, `PATCH /branches/{id}/status`, scoped al `company_id` del token.
  - Archivos/paquetes: `backend/branch/`
  - Depende de: BE-1.1, BE-1.3

**Semana 3**

- [ ] **BE-1.5** — CRUD `User` (`feature/user-crud`)
  - Qué implica: `GET/POST/PATCH /users`, `PATCH /users/{id}/status`, `GET /users/me`. Maneja rol y `branch_ids` cuando el rol es Supervisor.
  - Archivos/paquetes: `backend/user/`
  - Depende de: BE-1.1, BE-1.4

- [ ] **BE-1.6** — CRUD `Employee` + historial (`feature/employee-crud`)
  - Qué implica: `GET/POST/PATCH /employees`, `PATCH /employees/{id}/status` (cambia `estado_laboral`, sin bloquear por `estado_liquidacion = pendiente`, RF-07b), `GET /employees/me`, `GET /employees/{id}/history`.
  - Archivos/paquetes: `backend/employee/`
  - Depende de: BE-1.1, BE-1.4

- [ ] **BE-1.7** — Autorización por rol (RBAC) en toda la Fase 1 (`feature/rbac-phase1`)
  - Qué implica: `@PreAuthorize` por rol/alcance según la tabla de `api-design.md` sobre los endpoints ya creados, incluida la regla RF-29 (un `EMPLOYEE` nunca ve datos de otro) y la regla de 404-no-403 para recursos de otro tenant.
  - Archivos/paquetes: `backend/security/`, `backend/company/`, `backend/branch/`, `backend/user/`, `backend/employee/` (anotaciones)
  - Depende de: BE-1.3, BE-1.4, BE-1.5, BE-1.6

**Semana 4**

- [ ] **BE-1.8** (opcional) — Registro mínimo de auditoría (`feature/audit-log-minimal`)
  - Qué implica: adelanto opcional de un evento + persistencia genérica de `AuditLog` (sin pantalla ni endpoint de consulta todavía — eso es BE-5.1) para que Employee/Schedule tengan historial real cuando lleguemos a Fase 5. Salteable sin romper nada.
  - Archivos/paquetes: `backend/common/audit/`
  - Depende de: BE-1.6

- [ ] **BE-1.9** — Tests de aislamiento multi-tenant (`feature/tenant-isolation-tests`)
  - Qué implica: tests de integración que verifican que un usuario de la Company A nunca puede leer/escribir datos de la Company B (404, nunca 403) en los 4 módulos de esta fase. Es el checkpoint de verificación de toda la fase antes de pasar a frontend.
  - Archivos/paquetes: `backend/` — tests de company, branch, user, employee
  - Depende de: BE-1.7

### Frontend (`frontend/`)

**Semana 5**

- [ ] **FE-1.1** — Scaffold Angular + Tailwind + estructura de carpetas (`feature/angular-scaffold`)
  - Qué implica: `ng new`, configuración de Tailwind, armado de `core/`, `shared/`, `features/` vacíos según `CLAUDE.md`. Deja `ReactiveFormsModule` configurado y la convención de i18n desde el día 1 (sin strings hardcodeados en templates), aunque la traducción completa se resuelve recién en Fase 5.
  - Archivos/paquetes: `frontend/src/app` (estructura completa)
  - Depende de: nada

- [ ] **FE-1.2** — `core/` — auth, guards, interceptor JWT (`feature/core-auth`)
  - Qué implica: pantalla de login, `AuthService`, interceptor HTTP que agrega el Bearer token y maneja el refresh, guards de rol que leen el rol embebido en el JWT (nunca de la URL ni de un parámetro propio).
  - Archivos/paquetes: `frontend/core/`
  - Depende de: FE-1.1, BE-1.2

- [ ] **FE-1.3** — `shared/` — componentes UI base (`feature/ui-kit-base`)
  - Qué implica: botón, input, tabla, modal, card propios con Tailwind y tokens de diseño (colores/tipografía) — sin Angular Material, por decisión de producto.
  - Archivos/paquetes: `frontend/shared/`
  - Depende de: FE-1.1

**Semana 6**

- [ ] **FE-1.4** — `features/companies` (solo Super Admin) (`feature/companies-screen`)
  - Qué implica: alta y listado de empresas, visible únicamente para `SUPER_ADMIN`.
  - Archivos/paquetes: `frontend/features/companies/`
  - Depende de: FE-1.2, FE-1.3, BE-1.3

- [ ] **FE-1.5** — `features/branches` (`feature/branches-screen`)
  - Qué implica: CRUD de sucursales.
  - Archivos/paquetes: `frontend/features/branches/`
  - Depende de: FE-1.2, FE-1.3, BE-1.4

- [ ] **FE-1.6** — `features/employees` (`feature/employees-screen`)
  - Qué implica: listado con columnas separadas de estado laboral y estado de liquidación (ux-decisions.md #3), filtros por sucursal/estado, búsqueda, alta/edición.
  - Archivos/paquetes: `frontend/features/employees/`
  - Depende de: FE-1.5, BE-1.6

---

## Fase 2 — Disponibilidad, horarios y licencias

**Bloqueante de esta fase**: todo depende de que Employee y Branch existan (Fase 1 cerrada). Dentro de la fase, `Schedule` depende de `Availability`, y `LeaveRequest` depende de `Schedule` (chequeo de conflicto).

### Backend

**Semana 7**

- [ ] **BE-2.1** — CRUD `EmployeeAvailability` (`feature/availability-crud`)
  - Qué implica: carga y edición de franjas de disponibilidad por el propio empleado, sin aprobación (RF-08); lectura por RRHH/Supervisor (RF-09).
  - Archivos/paquetes: `backend/availability/`
  - Depende de: BE-1.6

- [ ] **BE-2.2** — CRUD `Holiday` (`feature/holiday-crud`)
  - Qué implica: feriados por empresa, opcionalmente por sucursal, con flag de recurrencia anual.
  - Archivos/paquetes: `backend/holiday/`
  - Depende de: BE-1.3, BE-1.4

**Semana 8**

- [ ] **BE-2.3** — CRUD `Schedule` + solapamiento + advertencia de disponibilidad (`feature/schedule-crud-overlap`)
  - Qué implica: el corazón de esta fase. `POST/PATCH /schedules` valida solapamiento contra **todos** los turnos del empleado en cualquier sucursal de la empresa (409 duro, RF-15); usa timestamp completo para turnos que cruzan medianoche; si el turno cae fuera de la disponibilidad declarada, responde 201 igual con `"warning": "OUT_OF_AVAILABILITY"` (RF-10) y lo registra en auditoría. Incluye `confirm` y cambio de estado (`cumplido`/`ausente`).
  - Archivos/paquetes: `backend/schedule/`
  - Depende de: BE-2.1, BE-1.4, BE-1.6

- [ ] **BE-2.4** — `POST /schedules/{id}/duplicate-weekly` (`feature/schedule-duplicate-weekly`)
  - Qué implica: soporte a horario fijo semanal (RF-11) duplicando un turno como plantilla recurrente.
  - Archivos/paquetes: `backend/schedule/`
  - Depende de: BE-2.3

**Semana 9**

- [ ] **BE-2.5** — CRUD `LeaveType` (`feature/leave-type-crud`)
  - Qué implica: tipos de licencia configurables por empresa (nombre, si es paga, si tiene cupo anual).
  - Archivos/paquetes: `backend/leave/`
  - Depende de: BE-1.3

- [ ] **BE-2.6** — `LeaveRequest` + flujo de aprobación (`feature/leave-request-approval`)
  - Qué implica: alta de solicitud (empleado o RRHH a nombre de un empleado), y `approve`/`reject`/`cancel` siguiendo el flujo de `flujos-proceso.md` #1: el chequeo de conflicto contra `Schedule` ocurre **antes** de confirmar la aprobación (409 con el detalle del conflicto, no un rechazo silencioso). Una vez aprobada, bloquea turnos superpuestos.
  - Archivos/paquetes: `backend/leave/`
  - Depende de: BE-2.5, BE-2.3

### Frontend

**Semana 10**

- [ ] **FE-2.1** — `features/availability` (`feature/availability-screen`)
  - Qué implica: pantalla para que el empleado cargue/edite su disponibilidad, y vista de solo lectura para RRHH/Supervisor al armar horarios.
  - Archivos/paquetes: `frontend/features/availability/`
  - Depende de: FE-1.6, BE-2.1

- [ ] **FE-2.2a** — Armado de horarios: grilla base (`feature/schedule-builder-base`)
  - Qué implica: la pantalla más compleja del sistema (ux-decisions.md #1) — línea de tiempo continua, no franjas fijas. Columnas por día, eje vertical de horas (rango configurable por `Branch.horario_visible_inicio/fin`, solo visual), navegación semanal, selector de sucursal, bloques de turno posicionados/dimensionados según hora real, huecos vacíos con control `+`.
  - Archivos/paquetes: `frontend/features/schedules/`
  - Depende de: FE-2.1, BE-2.3

**Semana 11**

- [ ] **FE-2.2b** — Armado de horarios: advertencias, conflictos y medianoche (`feature/schedule-builder-warnings`)
  - Qué implica: borde punteado ámbar + ícono para `OUT_OF_AVAILABILITY`, bloque de continuación "continúa de [día] [hora]hs" para turnos que cruzan medianoche, manejo visual del 409 de solapamiento, bloque de licencia aprobada (estilo diferenciado, no permite asignar turno encima).
  - Archivos/paquetes: `frontend/features/schedules/`
  - Depende de: FE-2.2a, BE-2.4, BE-2.6

- [ ] **FE-2.3** — `features/leaves` — solicitud y aprobación (`feature/leaves-screen`)
  - Qué implica: formulario de solicitud (empleado), listado con acciones directas de aprobar/rechazar (Supervisor/RRHH) que muestra la advertencia de conflicto en la card **antes** de que se intente aprobar (ux-decisions.md #4), anticipando el 409.
  - Archivos/paquetes: `frontend/features/leaves/`
  - Depende de: FE-2.2b, BE-2.6

---

## Fase 3 — Nómina

**Bloqueante de esta fase**: `Schedule`, `Holiday` y `LeaveRequest` (Fase 2) deben estar cerrados — el cálculo de liquidación depende de los tres.

### Backend

**Semana 12**

- [ ] **BE-3.1** — `PayrollConfig` GET/PUT (`feature/payroll-config`)
  - Qué implica: configuración única por empresa — umbral de hora extra, multiplicadores, conceptos de descuento, periodicidad de pago.
  - Archivos/paquetes: `backend/payroll/`
  - Depende de: BE-1.3

- [ ] **BE-3.2** — CRUD `PayrollPeriod` (`feature/payroll-period-crud`)
  - Qué implica: listar y abrir períodos de liquidación.
  - Archivos/paquetes: `backend/payroll/`
  - Depende de: BE-3.1

**Semana 13**

- [ ] **BE-3.3** — Strategy: hora extra y feriados (`feature/payroll-strategy-overtime-holiday`)
  - Qué implica: patrón Strategy (patrones-diseno.md) para el umbral configurable de hora extra y el multiplicador de feriado trabajado, consumido más adelante por el Builder de Payslip.
  - Archivos/paquetes: `backend/payroll/`
  - Depende de: BE-3.1, BE-2.2, BE-2.3

- [ ] **BE-3.4** — Decorator: conceptos de descuento (`feature/payroll-decorator-discounts`)
  - Qué implica: aplicación en cadena de los conceptos de `PayrollConfig` (impuestos, cargas sociales, etc.) sobre el bruto.
  - Archivos/paquetes: `backend/payroll/`
  - Depende de: BE-3.1

**Semana 14**

- [ ] **BE-3.5** — CRUD `Advance` (`feature/advance-crud`)
  - Qué implica: alta, listado y baja (solo si no descontado) de adelantos de sueldo.
  - Archivos/paquetes: `backend/advance/`
  - Depende de: BE-1.6, BE-3.2

- [ ] **BE-3.6** — Payslip Builder: cálculo (`feature/payslip-builder-calculation`)
  - Qué implica: patrón Builder siguiendo el orden exacto de `flujos-proceso.md` #2.2 — primero horas (normales + extra + feriado), después impacto de `LeaveRequest` (suma si es paga, resta si no), y **recién después** los descuentos (incluidos adelantos). Invertir ese orden da montos incorrectos si algún concepto es un % sobre el bruto — respetar el orden tal cual está documentado.
  - Archivos/paquetes: `backend/payslip/`
  - Depende de: BE-3.3, BE-3.4, BE-3.5, BE-2.6

**Semana 15**

- [ ] **BE-3.7** — Payslip Factory Method + State (`feature/payslip-factory-state`)
  - Qué implica: creación de Payslip normal vs. ajuste (Factory Method), y transiciones de estado `generado/pagado/anulado/ajuste` (State).
  - Archivos/paquetes: `backend/payslip/`
  - Depende de: BE-3.6

- [ ] **BE-3.8** — Cierre de período — Template Method (`feature/payroll-period-close`)
  - Qué implica: `POST /payroll-periods/{id}/close` siguiendo `flujos-proceso.md` #2.1 — 409 si ya está cerrado (idempotente), calcula el Payslip de todo empleado activo o de baja con `estado_liquidacion = pendiente` (RF-07b, no se excluyen los dados de baja), descuenta adelantos, marca el período cerrado y pasa `estado_liquidacion` a `al_dia`.
  - Archivos/paquetes: `backend/payroll/`, `backend/payslip/`, `backend/employee/`
  - Depende de: BE-3.6, BE-3.7

**Semana 16**

- [ ] **BE-3.9** — Reapertura de período — solo Admin (`feature/payroll-period-reopen`)
  - Qué implica: `POST /payroll-periods/{id}/reopen`, operación sensible restringida a `ADMIN`.
  - Archivos/paquetes: `backend/payroll/`
  - Depende de: BE-3.8

- [ ] **BE-3.10** — Anulación + ajuste de Payslip — RF-20b (`feature/payslip-void-adjustment`)
  - Qué implica: `POST /payslips/{id}/void` anula (estado `anulado`, se conserva como historial) y genera un nuevo Payslip `ajuste` vinculado por `payslip_original_id`, solo `ADMIN`. Confirma que no existe (ni debe existir) un endpoint de edición directa de un Payslip pagado.
  - Archivos/paquetes: `backend/payslip/`
  - Depende de: BE-3.7

- [ ] **BE-3.11** — PDF del recibo — Adapter (`feature/payslip-pdf-export`)
  - Qué implica: `GET /payslips/{id}/pdf` con OpenPDF/iText detrás de un Adapter reutilizable (para no reescribir la integración cuando llegue la exportación de reportes en Fase 4).
  - Archivos/paquetes: `backend/payslip/`, `backend/common/` (adapter)
  - Depende de: BE-3.7

### Frontend

**Semana 17**

- [ ] **FE-3.1** — `features/payroll` — configuración (`feature/payroll-config-screen`)
  - Archivos/paquetes: `frontend/features/payroll/`
  - Depende de: BE-3.1

- [ ] **FE-3.2** — `features/payroll` — períodos (`feature/payroll-periods-screen`)
  - Qué implica: listado, abrir, cerrar, reabrir período con las confirmaciones correspondientes.
  - Archivos/paquetes: `frontend/features/payroll/`
  - Depende de: FE-3.1, BE-3.8, BE-3.9

- [ ] **FE-3.3** — `features/advances` (`feature/advances-screen`)
  - Archivos/paquetes: `frontend/features/advances/`
  - Depende de: BE-3.5

**Semana 18**

- [ ] **FE-3.4** — `features/payslips` — card de recibo (`feature/payslips-screen`)
  - Qué implica: card con 3 bloques (haberes/descuentos/neto) y badge de estado (ux-decisions.md #2), historial de liquidaciones del empleado, y para los de estado `ajuste` una referencia visible al recibo original.
  - Archivos/paquetes: `frontend/features/payslips/`
  - Depende de: BE-3.7, BE-3.10

- [ ] **FE-3.5** — `features/payslips` — descarga PDF (`feature/payslips-pdf-download`)
  - Archivos/paquetes: `frontend/features/payslips/`
  - Depende de: FE-3.4, BE-3.11

---

## Fase 4 — Reportes

**Bloqueante**: horas trabajadas depende de `Schedule` (Fase 2); costo de nómina y adelantos pendientes dependen de que ya se hayan cerrado períodos reales (Fase 3).

### Backend

**Semana 19**

- [ ] **BE-4.1** — `GET /reports/hours-worked` (`feature/report-hours-worked`)
  - Archivos/paquetes: `backend/report/` (paquete nuevo)
  - Depende de: BE-2.3

- [ ] **BE-4.2** — `GET /reports/payroll-cost` (`feature/report-payroll-cost`)
  - Archivos/paquetes: `backend/report/`
  - Depende de: BE-3.8

- [ ] **BE-4.3** — `GET /reports/pending-advances` (`feature/report-pending-advances`)
  - Archivos/paquetes: `backend/report/`
  - Depende de: BE-3.5

**Semana 20**

- [ ] **BE-4.4** — Exportación de reportes (`feature/report-export`)
  - Qué implica: `GET /reports/{report}/export?format=pdf|csv`, reutilizando el Adapter de PDF de BE-3.11. PDF es prioridad, CSV va después (decisión #4 del documento de requerimientos) — si el tiempo aprieta, dejar CSV para más adelante y no bloquear el resto de la fase.
  - Archivos/paquetes: `backend/report/`, `backend/common/`
  - Depende de: BE-4.1, BE-4.2, BE-4.3, BE-3.11

### Frontend

- [ ] **FE-4.1** — `features/reports` (`feature/reports-screen`)
  - Qué implica: dashboards de los 3 reportes con filtros de sucursal/período y botones de exportación.
  - Archivos/paquetes: `frontend/features/reports/` (nuevo)
  - Depende de: BE-4.1, BE-4.2, BE-4.3, BE-4.4

---

## Fase 5 — Auditoría, i18n y pulido

### Backend

**Semana 21**

- [ ] **BE-5.1** — `AuditLog` completo (`feature/audit-log-complete`)
  - Qué implica: entidad genérica + Observer (eventos de dominio de Spring) capturando cambios sobre Employee/Schedule/Advance/Payslip/LeaveRequest, y `GET /audit-log` con filtros. Si hiciste BE-1.8, esto lo completa; si no, arranca de cero acá.
  - Archivos/paquetes: `backend/common/audit/`
  - Depende de: BE-1.7 (o BE-1.8 si se hizo)

- [ ] **BE-5.2** — Cobertura de tests → 80% en capa de servicio (`feature/test-coverage-80`)
  - Qué implica: RNF-04, con foco en `payroll/`/`payslip/` por criticidad.
  - Archivos/paquetes: `backend/` — tests (transversal)
  - Depende de: toda la Fase 3

**Semana 22**

- [ ] **BE-5.3** — Revisión de tiempos de respuesta (`feature/performance-review`)
  - Qué implica: RNF-07 — CRUD simples <300ms, cálculo de liquidación de un período <3s, con datos de volumen realista.
  - Archivos/paquetes: `backend/` (transversal)
  - Depende de: BE-3.8

- [ ] **BE-5.4** (opcional) — Hardening de producción (`feature/prod-hardening`)
  - Qué implica: revisión de headers de seguridad, HTTPS obligatorio, configuración de perfil `prod`.
  - Archivos/paquetes: `backend/security/`, `backend/application.yml` (perfil `prod`)
  - Depende de: BE-1.1

### Frontend

**Semana 23**

- [ ] **FE-5.1** — `features/audit` — visor de auditoría (`feature/audit-log-screen`)
  - Archivos/paquetes: `frontend/features/audit/` (nuevo, solo Admin)
  - Depende de: BE-5.1

- [ ] **FE-5.2** — i18n completo (`feature/i18n-setup`)
  - Qué implica: extracción de todos los strings de UI a archivos de traducción (`@angular/localize` o ngx-translate), solo español cargado en v1 pero arquitectura preparada.
  - Archivos/paquetes: `frontend/` — transversal (todos los `features/`)
  - Depende de: que las pantallas de Fase 1-4 ya existan (para extraer strings reales, no antes)

**Semana 24**

- [ ] **FE-5.3** — Identidad visual (`feature/visual-identity-polish`)
  - Qué implica: consolidar tokens de diseño (colores, tipografía, espaciados) en todas las pantallas — que no se note como plantilla sin personalizar.
  - Archivos/paquetes: `frontend/shared/`, `frontend/tailwind.config`
  - Depende de: FE-1.3

- [ ] **FE-5.4** — Pase de responsive (`feature/responsive-pass`)
  - Qué implica: RNF-05, usable desde tablet/celular.
  - Archivos/paquetes: `frontend/` (transversal)
  - Depende de: todas las pantallas de Fase 1-4

- [ ] **FE-5.5** — QA final end-to-end (`feature/final-qa-pass`)
  - Qué implica: checkpoint de verificación de todo el flujo — alta de empresa → empleados → horarios → licencias → cierre de período → recibo → reportes, con los 5 roles.
  - Archivos/paquetes: todo el repo (transversal)
  - Depende de: todo lo anterior

---

## Notas finales

- Los issues de frontend de una fase pueden arrancar en cuanto el backend correspondiente tenga aunque sea el contrato de API estable, sin esperar a que esté 100% pulido — pero si el backend todavía no existe, hay que mockear la respuesta (ya lo señala el `CLAUDE.md` de frontend).
- Cuando llegues a una fase, releé la sección correspondiente de `requerimientos-sistema-gestion-personal.md` antes de escribir el primer issue de esa fase en GitHub — este documento resume, no reemplaza, el detalle funcional.
- Como todo vive en un solo repo, cuidado con los merges paralelos entre `backend/` y `frontend/`: no hay conflicto de archivos entre carpetas, pero sí conviene mantener `main` funcionando en todo momento (si un PR de backend rompe el build, bloquea también los PRs de frontend que dependen de `main` actualizado).
- Si en el camino algo de esto deja de tener sentido (por ejemplo, BE-1.8 termina no siendo necesario, o el schedule builder necesita un tercer issue), ajustalo directamente en tu copia — este es un punto de partida, no un contrato.
