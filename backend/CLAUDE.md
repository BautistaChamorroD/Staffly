# CLAUDE.md — Staffly Backend

Este archivo es la guía de contexto para Claude Code (o cualquier agente) trabajando en este repositorio. Léelo completo antes de generar código.

## Qué es este proyecto

Staffly es un sistema **multi-tenant** (SaaS) de gestión de personal: empleados, horarios, disponibilidad, licencias, adelantos y liquidación de sueldos. El detalle funcional completo vive en `docs/requerimientos-sistema-gestion-personal.md`, el contrato de la API en `docs/api-design.md`, y los patrones de diseño ya decididos en `docs/patrones-diseno.md` — consultalos antes de tomar decisiones de modelado, alcance, endpoints, o estructura interna de un módulo. No agregues funcionalidad, endpoints, ni una estructura interna distinta a la ya decidida sin preguntar primero.

Cliente inicial: una heladería (una sola sucursal), pero el sistema se diseña desde el día 1 para soportar múltiples empresas (tenants) sin refactor.

## Stack

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.x |
| Persistencia | Spring Data JPA |
| Base de datos | PostgreSQL (prod), H2 en memoria (dev/test) |
| Migraciones | Flyway |
| Seguridad | Spring Security + JWT (`jjwt`) — access token corto + refresh token |
| Docs de API | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5 + Mockito |
| Cobertura | JaCoCo (objetivo: 80% en capa de servicio, especialmente lógica de nómina) |

No propongas cambios de stack sin que se te pida explícitamente.

## Principio arquitectónico no negociable: multi-tenant por `company_id`

Toda entidad de negocio (excepto `Company`) pertenece a una empresa y lleva `company_id`. El aislamiento se garantiza a nivel de persistencia, no solo de lógica de aplicación:

- Toda entidad de negocio extiende `TenantAwareEntity` (paquete `tenant/`), que define el campo `company_id` y un filtro de Hibernate (`@FilterDef`/`@Filter`) llamado `tenantFilter`.
- El filtro se activa en cada request vía `TenantFilterInterceptor`, tomando el `company_id` **exclusivamente** del JWT del usuario autenticado (`StafflyUserPrincipal`).
- **Regla dura: el `company_id` nunca se toma de un parámetro de la URL, del body de la request, ni de ningún input del cliente.** Si en algún punto necesitás el tenant actual dentro de un servicio, obtenelo del `SecurityContextHolder` / principal autenticado, nunca de un DTO de entrada.
- Cualquier query que use `EntityManager`/`Session` directamente (bypaseando el filtro) es sospechosa por defecto — evitalo salvo que sea estrictamente necesario y esté justificado en un comentario.

## Organización del código: por módulo de dominio, no por capa técnica

```
com.staffly.backend
├── tenant/       → infraestructura multi-tenant (no es un módulo de negocio)
├── company/      → Company
├── branch/       → Branch
├── user/         → User, Role, UserStatus (cuenta de acceso)
├── employee/     → Employee, EstadoLaboral, EstadoLiquidacion
├── availability/ → EmployeeAvailability
├── schedule/     → Schedule/Shift
├── holiday/      → Holiday
├── leave/        → LeaveType, LeaveRequest
├── payroll/      → PayrollConfig, PayrollPeriod
├── advance/      → Advance
├── payslip/      → Payslip
├── security/     → JWT filter, configuración de Spring Security
└── common/audit/ → AuditLog genérico
```

Dentro de cada paquete de módulo va todo lo relacionado a esa entidad: la entidad JPA, su enum de estado (si tiene), repositorio, servicio y controller. **No crear paquetes raíz `controllers/`, `services/`, `repositories/`** — la organización es por dominio.

## Convenciones de nombres

- Nombres de campos y tablas en **español** (`estado_laboral`, `fecha_ingreso`), consistente con el dominio del negocio y con el documento de requerimientos. Nombres de clases Java, métodos y paquetes en inglés/convención estándar Java donde no haya término de dominio específico.
- Enums de estado viven en el mismo paquete que la entidad a la que describen (ej. `EstadoLaboral` vive en `employee/`, no en un paquete `enums/` global).
- IDs son siempre `UUID`, generados por la base (`@GeneratedValue`), nunca autoincrementales — necesario para no filtrar información de volumen entre tenants y para facilitar sincronización futura si hiciera falta.

## Reglas de negocio críticas a respetar al implementar (no reinventar, ya están decididas)

Estas decisiones ya fueron tomadas y documentadas en el documento de requerimientos — implementalas tal cual, no las reabras salvo que el usuario lo pida:

- **Turnos que cruzan medianoche**: `Schedule` usa timestamp completo (fecha+hora), nunca solo "hora". Las horas se imputan al período de nómina donde el turno *inicia*.
- **Solapamiento de turnos**: se valida contra *todos* los turnos del empleado en cualquier sucursal de la empresa, no solo dentro de una misma sucursal.
- **Disponibilidad declarada por el empleado**: no requiere aprobación. Asignar un turno fuera de la disponibilidad declarada genera una advertencia (no bloqueo) y se registra en `AuditLog`.
- **Solicitudes de licencia (`LeaveRequest`)**: sí requieren aprobación de Supervisor/RRHH antes de confirmarse, y bloquean la asignación de turnos en su rango de fechas una vez aprobadas.
- **Corrección de una liquidación (`Payslip`) ya pagada**: nunca se edita directo. Se anula (estado `ANULADO`) y se genera un nuevo `Payslip` de estado `AJUSTE` vinculado al original. Solo rol `ADMIN` puede autorizar esto.
- **Baja de empleado con saldos pendientes**: no se bloquea la baja. Se marca `estado_liquidacion = PENDIENTE` y se genera igual la liquidación final cuando corresponda.
- **Hora extra**: umbral simple configurable por empresa (`PayrollConfig`), no reglas legales complejas por país.
- **Privacidad entre empleados**: un usuario con rol `EMPLOYEE` solo puede ver su propio registro, nunca el de otro empleado, sin excepciones.

## Roles del sistema

`SUPER_ADMIN` (plataforma, no pertenece a ninguna empresa) · `ADMIN` (dueño de la empresa) · `RRHH` · `SUPERVISOR` (alcance limitado a sus sucursales asignadas) · `EMPLOYEE` (alcance limitado a su propio registro).

## Comandos habituales

```bash
./mvnw spring-boot:run              # levantar en dev (perfil dev, H2 en memoria)
./mvnw test                          # correr tests
./mvnw verify                        # tests + reporte de cobertura JaCoCo
```

Perfil por defecto: `dev`. Consola H2 disponible en `/h2-console` en dev.

## Orden de construcción sugerido (no saltear fases sin avisar)

1. **Fase 1**: `tenant`, `company`, `branch`, `employee`, `user`, `security` (JWT + login) — ya scaffoldeado el modelo base, falta security y CRUDs.
2. **Fase 2**: `availability`, `schedule`, `holiday`, `leave`.
3. **Fase 3**: `payroll`, `advance`, `payslip` (incluye lógica de cálculo de liquidación, la parte más sensible del sistema — requiere buena cobertura de tests).
4. **Fase 4**: reportes.
5. **Fase 5**: `common/audit` completo, exportaciones PDF, pulido.

Si te piden implementar algo de una fase posterior sin haber cerrado la anterior, señalalo antes de arrancar.

## Qué NO hacer sin preguntar

- No agregar autenticación externa (Keycloak/Auth0/OAuth) — ya se decidió JWT propio.
- No agregar Angular Material ni ninguna librería de componentes UI en el frontend — ya se decidió Tailwind puro con componentes propios.
- No implementar control de asistencia/fichaje, integraciones gubernamentales, ni billing del SaaS — están explícitamente fuera de alcance de v1 (ver sección 10 del documento de requerimientos).
- No hardcodear reglas de nómina de un país específico — todo vía `PayrollConfig`.

## Convención de commits

Este repo usa **Conventional Commits**, con mensajes **en español**: tipo + descripción breve en minúsculas.

Tipos permitidos: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.

Ejemplo: `feat: agregar endpoint de alta de empleado`.

El agente debe respetar esta convención en cada commit que haga a partir de ahora.
