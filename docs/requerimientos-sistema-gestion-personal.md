# Sistema de Gestión de Personal — Documento de Requerimientos

**Versión:** 1.5
**Estado:** Requerimientos v1 cerrados y revisados en profundidad. Incluye gestión básica de licencias/vacaciones y diseño de UX de la pantalla de horarios (línea de tiempo continua). Listo para pasar a diseño técnico detallado y scaffolding.
**Última actualización:** Julio 2026

---

## 1. Visión general del producto

### 1.1 Propósito

Sistema **multi-tenant** (multi-empresa) para la gestión integral de personal, orientado a PyMEs con empleados en relación de dependencia. Permite administrar empleados, horarios, disponibilidad, control de sueldos, adelantos y liquidaciones, de forma centralizada y escalable.

El sistema nace para atender a un cliente inicial (heladería, sucursal única) pero se diseña desde el día 1 para ser comercializado como SaaS a múltiples empresas, sin necesidad de desarrollos a medida por cliente.

### 1.2 Fuera de alcance explícito (v1)

Estas áreas quedan **fuera del alcance** de la v1 salvo aprobación expresa del cliente/product owner:

- Gestión de compras, ventas, stock o facturación.
- Control de asistencia por fichaje (biométrico, checkin/checkout). Se deja **documentado como extensión futura** (ver sección 10).
- Integración con organismos gubernamentales/AFIP/ARCA/seguridad social (se modela de forma configurable, pero no se implementan conectores reales en v1).
- Facturación/cobranza del propio SaaS a sus clientes (billing del producto).
- App móvil nativa (se contempla que el frontend web sea responsive/mobile-friendly).

### 1.3 Principios de diseño

- **Multi-tenant desde el modelo de datos**: toda entidad de negocio pertenece a una `Company`. Aislamiento por columna discriminadora (`company_id`), no por base de datos separada (más simple de operar y mantener para el segmento PyME).
- **Configurable, no hardcodeado**: reglas de nómina, tipos de licencia, feriados, etc. se modelan como configuración por empresa/país, no como lógica fija en código.
- **Escalable en roles y sucursales**: una empresa puede tener 1 o N sucursales desde el modelo, aunque el cliente inicial solo use una.
- **Auditable**: cambios sobre datos sensibles (sueldos, horarios, adelantos) quedan trazados (quién, cuándo, qué valor anterior/nuevo).

---

## 2. Actores y roles

| Rol | Descripción | Alcance |
|---|---|---|
| **Super Admin** (plataforma) | Administra el SaaS en sí: altas de empresas (tenants), planes, soporte. No opera datos de negocio de las empresas. | Global (todas las empresas) |
| **Admin / Dueño** | Control total sobre su empresa: configuración, sucursales, roles, nómina, reportes. | Una `Company` |
| **RRHH / Encargado** | Gestiona personal, horarios, disponibilidad, adelantos y liquidaciones. No accede a configuración global de la empresa. | Una `Company` (todas sus sucursales o las asignadas) |
| **Supervisor** | Gestiona horarios y disponibilidad de su(s) sucursal(es)/área(s) asignada(s). Visibilidad acotada a su equipo. | Una o más `Branch` dentro de una `Company` |
| **Empleado** | Consulta su propio horario, disponibilidad, historial de pagos/adelantos y su recibo de sueldo. Puede cargar su disponibilidad. | Su propio registro |

> **Nota de diseño**: se modela como permisos granulares (no roles fijos hardcodeados) para poder escalar a roles custom por empresa en el futuro sin romper el modelo.

> **Nota de diseño (User vs. Employee)**: el acceso al sistema (`User`) y el registro de gestión de personal (`Employee`) son entidades separadas y se relacionan de forma opcional. Esto permite, por ejemplo, que un Admin/Dueño tenga usuario sin estar cargado como empleado en nómina, o que un empleado exista en el sistema sin necesitar acceso propio (ej. si RRHH carga todo por él). Ver detalle en sección 3.2.

---

## 3. Modelo de dominio (entidades principales)

### 3.1 Jerarquía multi-tenant

```
Company (empresa/tenant)
 ├── Branch (sucursal) [1..N, mínimo 1]
 │    └── Employee (empleado) [asignado a 1..N sucursales]
 └── User (cuenta de acceso al sistema) [relación opcional 1 a 1 con Employee]
```

### 3.2 Entidades

#### Company
- id, nombre, razón social, país (define reglas de nómina), moneda, zona horaria, estado (activa/suspendida), fecha de alta, plan (referencia a futuro billing).

#### Branch (Sucursal)
- id, company_id, nombre, dirección, zona horaria (por si difiere de la empresa), estado (activa/inactiva).
- **horario_visible_inicio / horario_visible_fin** (opcional): rango horario por defecto que se muestra en la pantalla de armado de horarios (ej. 08:00 a 02:00), para no mostrar una línea de tiempo de 24hs completas si el negocio no opera en horas de madrugada. Es solo una preferencia visual de UI — no restringe en qué horario se puede asignar un turno.

#### User (Usuario del sistema)
- id, company_id, employee_id (opcional, FK a `Employee` — puede ser nulo si el usuario no está asociado a un registro de nómina, ej. un Admin/Dueño que no cobra sueldo por este sistema), email, password_hash, rol (Admin/RRHH/Supervisor/Empleado), branch_ids (si el rol es Supervisor, limita su alcance), estado (activo/inactivo).
- **Separación de responsabilidades**: `User` es la cuenta de acceso al sistema (login, rol, permisos). `Employee` es el registro de gestión de personal (datos laborales, sueldo, horarios). Se relacionan de forma **opcional 1 a 1**: no todo `Employee` tiene necesariamente un `User` (podría no requerir acceso al sistema), y no todo `User` corresponde a un `Employee` (ej. el Admin/Dueño puede no estar cargado como empleado en nómina).

#### Employee (Empleado)
- id, company_id, branch_ids (una o más sucursales asignadas), datos personales (nombre, apellido, DNI/documento, fecha de nacimiento, contacto), fecha de ingreso, fecha de egreso (si aplica), tipo de contrato (jornada completa/parcial, por hora, etc.), categoría/puesto, sueldo base o valor hora, foto (opcional).
- **estado_laboral**: `activo` / `licencia` / `suspendido` / `baja`. Refleja la situación de relación de dependencia del empleado.
- **estado_liquidacion**: `al_dia` / `pendiente`. Refleja si el empleado tiene saldos sin liquidar (adelantos sin descontar, o pertenece a un `PayrollPeriod` todavía abierto). Estos dos campos son **independientes entre sí**: un empleado puede estar `baja` + `pendiente` (se fue y falta su liquidación final), `activo` + `pendiente` (adelanto tomado recientemente, situación normal), etc. Evita representar estados imposibles o ambiguos con un único campo combinado.

#### EmployeeAvailability (Disponibilidad)
- id, employee_id, día de semana o fecha específica, franja horaria disponible, tipo (recurrente semanal / excepción puntual), estado (aprobada/pendiente si se requiere validación de RRHH).

#### Schedule / Shift (Horario / Turno)
- id, employee_id, branch_id, **fecha_hora_inicio** (timestamp completo, no solo hora), **fecha_hora_fin** (timestamp completo), tipo de turno (fijo/rotativo), estado (planificado/confirmado/cumplido/ausente), horas totales calculadas.
- Soporta dos modalidades de definición (según lo acordado):
  - **Horario fijo semanal**: plantilla que se repite semana a semana hasta que se modifique.
  - **Turno rotativo**: se carga semana a semana, puede variar.
- **Turnos que cruzan la medianoche**: se modelan con timestamp de fecha+hora completo (no solo "hora"), de modo que un turno de 22:00 de un día a 06:00 del día siguiente se calcule sin ambigüedad. A efectos de nómina, las horas de un turno que cruza medianoche se imputan íntegramente al período de nómina donde **inicia** el turno (regla fija, para evitar ambigüedad de reparto entre dos períodos).

#### Holiday (Feriado)
- id, company_id, branch_id (opcional — nulo si aplica a todas las sucursales de la empresa), fecha, nombre, recurrente (booleano, si se repite cada año en la misma fecha). Se consulta desde el cálculo de nómina (RF-17) para determinar si un turno cayó en feriado, y desde `PayrollConfig` para el multiplicador correspondiente.

#### PayrollConfig (Configuración de nómina, por Company)
- id, company_id, país, moneda, reglas configurables:
  - **umbral de hora extra**: cantidad de horas diarias y/o semanales a partir de la cual se considera hora extra (ej. "más de 8hs/día" o "más de 48hs/semana"), configurable por empresa.
  - valor hora normal / hora extra (multiplicador, ej. x1.5, x2)
  - multiplicador aplicable a horas trabajadas en feriado (ver entidad `Holiday`)
  - conceptos de descuento (impuestos, cargas sociales, sindicato, obra social) — modelados como lista de "conceptos" con nombre, tipo (%, monto fijo), y si aplica sobre el bruto o el neto.
  - periodicidad de pago (semanal/quincenal/mensual)

#### Advance (Adelanto de sueldo)
- id, employee_id, fecha, monto, motivo, estado (pendiente/descontado), período de nómina al que se imputa.

#### PayrollPeriod (Período de liquidación)
- id, company_id, fecha inicio, fecha fin, estado (abierto/cerrado/pagado).

#### Payslip (Liquidación / Recibo de sueldo)
- id, employee_id, payroll_period_id, horas normales, horas extra, feriados trabajados, total bruto, detalle de conceptos (impuestos/descuentos aplicados), adelantos descontados, total neto, fecha de pago, **estado** (generado/pagado/anulado/ajuste), **payslip_original_id** (opcional, FK al Payslip que este ajusta, solo aplica si estado = `ajuste`).

#### LeaveType (Tipo de licencia, configurable por Company)
- id, company_id, nombre (ej. "Vacaciones", "Enfermedad", "Licencia sin goce de sueldo"), es_paga (booleano), tiene_cupo_anual (booleano), cupo_dias_anual (opcional, solo si tiene_cupo_anual = true).

#### LeaveRequest (Solicitud de licencia/vacaciones)
- id, employee_id, leave_type_id, fecha_inicio, fecha_fin, motivo (opcional), estado (pendiente/aprobada/rechazada/cancelada), aprobado_por (user_id, opcional hasta que se resuelva). Una solicitud aprobada bloquea la asignación de turnos (`Schedule`) del empleado en ese rango de fechas, y se considera en el cálculo de `Payslip` según si el `LeaveType` es paga o no.
- **Fuera de alcance en esta primera versión de la funcionalidad** (documentado como extensión futura, ver sección 10): cálculo automático de cupo según antigüedad/legislación, licencias fraccionadas por horas (solo por día completo en v1), adjuntar certificados médicos u otra documentación de respaldo.

#### AuditLog (Auditoría)
- id, company_id, user_id (quién realizó el cambio, FK a `User`), entidad afectada, id de entidad afectada, campo, valor anterior, valor nuevo, timestamp. Relación genérica: `entidad` + `entidad_id` permiten referenciar cualquier tabla del sistema (Employee, Schedule, Advance, etc.) sin necesidad de una tabla de auditoría por entidad.

---

## 4. Requerimientos funcionales

### 4.1 Gestión de empresas y sucursales (multi-tenant)
- RF-01: Alta, edición y baja lógica de empresas (Super Admin). El alta de una `Company` nueva incluye la creación del primer `User` con rol Admin (contraseña provisoria, forzada a cambiar en el primer login). No hay registro self-service en v1 — toda alta de empresa la realiza el Super Admin.
- RF-02: Cada empresa puede tener una o más sucursales; alta/edición/baja de sucursales (Admin).
- RF-03: Aislamiento total de datos entre empresas (ningún usuario ve datos de otro tenant).

### 4.2 Gestión de empleados
- RF-04: Alta, edición, baja (lógica) de empleados.
- RF-05: Asignación de empleado a una o más sucursales.
- RF-06: Historial de cambios de puesto/categoría/sueldo (auditable).
- RF-07: Carga de documentación asociada al empleado (opcional, a definir formato/almacenamiento).
- RF-07b: Un empleado puede darse de baja (`estado_laboral = baja`) en cualquier momento, incluso con adelantos sin descontar o perteneciendo a un `PayrollPeriod` abierto. La baja no se bloquea por saldos pendientes: el sistema marca `estado_liquidacion = pendiente` y mantiene visible el saldo a liquidar hasta que se genere su `Payslip` de cierre ("liquidación final").

### 4.3 Disponibilidad
- RF-08: El empleado puede cargar y modificar su disponibilidad horaria (días y franjas) **sin necesidad de aprobación previa** — es información que solo el propio empleado conoce, se toma como válida al cargarla.
- RF-09: RRHH/Supervisor puede ver la disponibilidad de todos los empleados de su alcance al momento de armar horarios.
- RF-10: Si se asigna un turno fuera de la disponibilidad declarada del empleado, el sistema muestra una **advertencia visual** (no bloqueo duro) y permite al supervisor confirmar la asignación igual. La asignación fuera de disponibilidad queda registrada en el log de auditoría (RF-28), para poder detectar patrones (ej. un empleado asignado reiteradamente fuera de su disponibilidad declarada).

### 4.4 Horarios y turnos
- RF-11: Definición de horario fijo semanal por empleado (plantilla recurrente).
- RF-12: Definición de turnos rotativos semana a semana.
- RF-13: Vista semanal/mensual de horarios por sucursal y por empleado. La pantalla de armado de horarios se presenta como una **línea de tiempo continua** (columnas por día, eje vertical de horas), con cada turno posicionado y dimensionado según su hora de inicio/fin real — no como franjas fijas predefinidas (mañana/tarde/noche). Esto permite representar turnos de cualquier duración y turnos que cruzan medianoche de forma visualmente natural (el bloque continúa en el día siguiente con una referencia "continúa de..."). El rango horario visible por defecto es configurable por sucursal (`horario_visible_inicio/fin` en `Branch`), solo a fines de visualización.
- RF-14: Cálculo automático de horas totales trabajadas por período según horario cargado.
- RF-15: Detección de solapamiento de turnos para un mismo empleado, **incluyendo entre distintas sucursales** de la misma empresa (un empleado no puede tener dos turnos que se superpongan en el tiempo, sin importar si son en la misma sucursal o en sucursales distintas). Bloqueo duro, no advertencia (a diferencia del caso de disponibilidad, ver decisión #6 en sección 8).

### 4.4b Licencias y vacaciones
- RF-15b: Configuración de tipos de licencia por empresa (`LeaveType`): nombre, si es paga, si tiene cupo anual de días.
- RF-15c: El empleado puede solicitar una licencia/vacación indicando tipo, fecha inicio y fecha fin.
- RF-15d: La solicitud de licencia requiere aprobación de Supervisor o RRHH antes de quedar confirmada (a diferencia de la disponibilidad, que no requiere aprobación — ver decisión #5 en sección 8).
- RF-15e: Una licencia aprobada bloquea la asignación de turnos al empleado en ese rango de fechas (no se puede programar un `Schedule` que se superponga con una licencia aprobada).
- RF-15f: El cálculo de liquidación (RF-17) considera los días de licencia aprobados en el período: si el `LeaveType` es paga, se incluye en el bruto; si no es paga, se descuentan esos días del cálculo.

### 4.5 Nómina y liquidación de sueldos
- RF-16: Configuración de reglas de nómina por empresa (moneda, valor hora, multiplicadores de hora extra, feriados, conceptos de descuento). El criterio de "hora extra" se define como un **umbral simple configurable** por empresa (ej. más de N horas diarias o semanales), no como reglas legales complejas por país/convenio.
- RF-17: Cálculo automático de liquidación por período: horas normales + horas extra + feriados trabajados − descuentos (impuestos/cargas sociales configurables) − adelantos del período = neto a pagar.
- RF-18: Generación de recibo de sueldo (Payslip) por empleado y período.
- RF-19: Registro de adelantos de sueldo (fecha, monto, motivo) y su descuento automático en la siguiente liquidación.
- RF-20: Cierre de período de nómina (bloquea modificaciones posteriores salvo reapertura autorizada por rol Admin).
- RF-20b: Un `Payslip` ya cerrado y pagado **no se edita directamente**. Su corrección se hace anulándolo (estado `anulado`, se conserva como historial) y generando un nuevo `Payslip` de estado `ajuste` vinculado al original (`payslip_original_id`), con el detalle recalculado. Solo el rol Admin puede autorizar esta operación.
- RF-21: Historial de liquidaciones por empleado, incluyendo anulaciones y ajustes.

### 4.6 Reportes
- RF-22: Reporte de horas trabajadas por empleado/sucursal/período.
- RF-23: Reporte de costos de nómina por sucursal/empresa/período.
- RF-24: Reporte de adelantos pendientes.
- RF-25: Exportación de reportes (CSV/PDF — a definir prioridad).

### 4.7 Seguridad y accesos
- RF-26: Autenticación de usuarios (login).
- RF-27: Autorización basada en roles y alcance (empresa/sucursal/propio registro).
- RF-28: Auditoría de cambios sobre datos sensibles (sueldos, horarios, adelantos).
- RF-29: Un usuario con rol Empleado únicamente puede ver y consultar su propio registro (horario, disponibilidad, adelantos, recibos). No tiene acceso a datos de ningún otro empleado, bajo ninguna circunstancia.

---

## 5. Requerimientos no funcionales

| Código | Requerimiento |
|---|---|
| RNF-01 | El sistema debe soportar múltiples empresas (tenants) sin degradación de performance perceptible entre ellas. |
| RNF-02 | Aislamiento de datos entre tenants debe estar garantizado a nivel de capa de persistencia (no solo por lógica de aplicación). |
| RNF-03 | El backend debe exponer una API REST documentada (OpenAPI/Swagger). |
| RNF-04 | Cobertura de tests unitarios mínima objetivo: 80% en capas de servicio (lógica de nómina en particular, por criticidad). |
| RNF-05 | El sistema debe ser responsive (usable desde tablet/celular), sin necesidad de app nativa en v1. |
| RNF-06 | Los cálculos de nómina deben ser reproducibles y trazables (dado un período, se puede reconstruir cómo se llegó al neto). |
| RNF-07 | Tiempos de respuesta objetivo: operaciones CRUD simples < 300ms, cálculo de liquidación de un período < 3s (a validar con volumen real). |
| RNF-08 | Internacionalización: fechas, moneda y formato numérico según configuración de la empresa (no hardcodear a un país). Textos de interfaz en archivos de traducción separados desde v1 (solo español cargado, arquitectura preparada para sumar idiomas sin retrabajo). |
| RNF-09 | Autenticación stateless vía JWT (access token corto + refresh token), con `company_id` embebido en el token como única fuente de verdad para el filtro multi-tenant en cada request. |

---

## 6. Stack tecnológico

### 6.1 Backend

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot (última versión estable compatible) |
| Persistencia | Spring Data JPA |
| Base de datos (dev/test) | H2 en memoria |
| Base de datos (producción) | **PostgreSQL** |
| Web | Spring Web (REST) |
| Seguridad | Spring Security + JWT (access + refresh token, ver sección 8, decisión #1) |
| Testing | JUnit 5 + Mockito |
| Cobertura | JaCoCo |
| Documentación API | springdoc-openapi (Swagger UI) |
| Migraciones de esquema | Flyway |

### 6.2 Frontend

| Capa | Tecnología |
|---|---|
| Framework | Angular (última versión estable) |
| Formularios | ReactiveFormsModule (`FormBuilder`, `FormGroup`, `FormControl`) |
| Estilos | Tailwind CSS |
| HTTP | `HttpClient` |
| Reactividad | RxJS |
| Componentes UI | Componentes propios con Tailwind CSS (sin Angular Material, ver sección 8, decisión #2) |
| Internacionalización | `@angular/localize` o ngx-translate — *a definir en diseño técnico*, con solo español cargado en v1 |
| Gráficos (reportes) | *a definir si se necesitan visualizaciones (ej. ngx-charts, Chart.js)* |
| Generación de PDF | *a definir en diseño técnico backend (ej. OpenPDF, iText) para recibos de sueldo y reportes* |

---

## 7. Estrategia multi-tenant (detalle técnico)

**Enfoque elegido: discriminador por columna (`company_id`)** en lugar de esquema-por-tenant o base-de-datos-por-tenant.

Ventajas para este caso:
- Un solo despliegue, una sola base de datos a mantener/backupear.
- Simplifica migraciones de esquema (se aplican una vez, no N veces).
- Costo operativo bajo, ideal para volumen esperado (PyMEs).

Consideraciones de implementación a resolver en diseño técnico (no en este documento de requerimientos, pero se deja registrado):
- Uso de un filtro/interceptor a nivel de aplicación (ej. Hibernate Filters, o un `@PrePersist`/`@Where` con `company_id` del contexto de sesión) para evitar fugas de datos entre tenants por error humano.
- El `company_id` del usuario autenticado debe derivarse del token de sesión, nunca de un parámetro enviado por el cliente.

---

## 8. Decisiones de diseño cerradas (ex puntos abiertos)

Estos temas impactaban el modelo o la arquitectura y fueron resueltos antes de avanzar a diseño técnico detallado:

| # | Tema | Decisión |
|---|---|---|
| 1 | **Autenticación** | JWT propio con Spring Security (no Keycloak/Auth0 en esta etapa). Access token de corta duración (15-30 min) + refresh token. El payload del JWT incluye `user_id`, `company_id`, `role` y `branch_ids` (si aplica). El `company_id` usado para el filtro multi-tenant se deriva siempre del token, nunca de un parámetro enviado por el cliente. Passwords hasheadas con BCrypt. HTTPS obligatorio en producción. |
| 2 | **Componentes UI (Angular)** | Tailwind CSS puro, con librería de componentes propios (botones, inputs, tablas, modales, cards) construida una vez y reutilizada. Se descarta Angular Material para mantener identidad visual propia de marca (Staffly), dado que es un producto a comercializar y no una herramienta interna. |
| 3 | **Notificaciones** | Fuera de alcance de v1. Se documenta como extensión futura (ver sección 10): envío de email al empleado cuando se publica su horario o se genera su recibo de sueldo. |
| 4 | **Exportación de reportes/recibos** | Prioridad: **PDF primero** (recibos de sueldo y reportes formales), **Excel/CSV después** (para análisis de datos en reportes gerenciales). |
| 5 | **Aprobación de disponibilidad** | No requiere aprobación. El empleado carga y modifica su disponibilidad libremente; se toma como válida de inmediato (ver RF-08). |
| 6 | **Turno fuera de disponibilidad declarada** | Advertencia visual al supervisor al momento de asignar, no bloqueo duro. La asignación puede confirmarse igual y queda registrada en auditoría (ver RF-10). |
| 7 | **Idioma de la interfaz** | Español únicamente en v1. La arquitectura se prepara desde el inicio para i18n (todos los textos de UI en archivos de traducción separados), para poder sumar idiomas más adelante sin retrabajo. |
| 8 | **Turnos que cruzan medianoche** | Sí existen (ej. cierre de local a la madrugada). Se modelan con timestamp de fecha+hora completo en `Schedule/Shift` (no solo "hora"). Las horas se imputan al período de nómina donde **inicia** el turno (ver detalle en sección 3.2, entidad Schedule/Shift). |

---

## 9. Roadmap sugerido de construcción (alto nivel)

No es parte del requerimiento funcional, pero ayuda a priorizar:

1. **Fase 1 — Core multi-tenant**: modelo de Company/Branch/Employee/User, autenticación y roles básicos.
2. **Fase 2 — Disponibilidad, horarios y licencias**: carga de disponibilidad, definición de horarios fijos y rotativos, solicitud y aprobación de licencias/vacaciones.
3. **Fase 3 — Nómina**: configuración de reglas por empresa, feriados, cálculo de liquidación (incluyendo impacto de licencias), adelantos, recibos, ajustes/anulaciones.
4. **Fase 4 — Reportes**: reportes de horas y costos.
5. **Fase 5 — Pulido y escalabilidad**: auditoría completa, exportaciones, notificaciones, i18n si aplica.

---

## 10. Extensiones futuras (documentadas, fuera de v1)

Se documentan para no perder el diseño mental, pero **no se implementan** en esta etapa salvo aprobación expresa:

- **Control de asistencia real**: fichaje por el propio empleado (checkin/checkout desde su usuario) y/o integración con hardware biométrico. El modelo de `Schedule` ya contempla un campo de estado (`planificado/confirmado/cumplido/ausente`) que podría alimentarse desde un módulo de asistencia futuro sin romper el diseño actual.
- **Integración con organismos de gobierno** (presentaciones de cargas sociales según país).
- **Billing del propio SaaS** (cobro a las empresas clientes por su suscripción).
- **App móvil nativa** para empleados (consulta de horario/recibo desde el celular).
- **Licencias — funcionalidad avanzada**: la gestión básica de licencias/vacaciones (`LeaveType`, `LeaveRequest`) ya forma parte del alcance de v1 (ver sección 4.4b). Quedan fuera de v1 y documentadas para más adelante: cálculo automático de cupo según antigüedad/legislación local, licencias fraccionadas por horas (solo por día completo en v1), y adjuntar certificados médicos u otra documentación de respaldo.

---

## 11. Glosario

| Término | Definición |
|---|---|
| Tenant | Empresa cliente del SaaS, con sus datos aislados del resto. |
| Branch | Sucursal o punto de trabajo dentro de una empresa. |
| Payroll | Proceso de liquidación de sueldos. |
| Payslip | Recibo de sueldo generado para un empleado en un período. |
| Advance | Adelanto de sueldo otorgado a un empleado, a descontar de su próxima liquidación. |
| Leave / LeaveRequest | Solicitud de licencia o vacaciones de un empleado, sujeta a aprobación. |
| LeaveType | Tipo de licencia configurable por empresa (vacaciones, enfermedad, etc.), con reglas propias de pago y cupo. |
