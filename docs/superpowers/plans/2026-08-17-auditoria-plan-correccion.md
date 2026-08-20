# Plan de corrección — Auditoría técnica 2026-08-17

**Fuente:** [Informe de auditoría](https://claude.ai/code/artifact/d627e571-6a47-4f49-9e8c-c4fd1c14c69b) — 5 auditorías de dominio en paralelo, 40 hallazgos, 511 tests reales corridos, contra `docs/requerimientos-sistema-gestion-personal.md`, `docs/api-design.md`, `docs/erd-staffly.md`, `docs/flujos-proceso.md`, `docs/ux-decisions.md`.

**Alcance de este plan:** los 40 hallazgos del informe, **sin filtrar por severidad** (Crítico a Bajo, todos entran), más todo RF marcado 🟡 Parcial o 🔴 Incorrecto en la matriz de cumplimiento — llevarlos a ✅ Correcto. No incluye el resto del roadmap (nada de lo clasificado ✅ Correcto se toca).

**Formato de fase:** una sola fase, sin escalonar por prioridad P0–P3 (eso quedó en el informe solo como referencia de lectura). Los 36 ítems de abajo son el universo completo a convertir en issues — el orden dentro de cada grupo es por dependencia técnica, no por urgencia.

**Siguiente paso:** confirmar este documento → crear 1 issue de GitHub por ítem (título ya redactado en convención `tipo(módulo): descripción`, consistente con los issues #101–#105 ya existentes en el repo) → cada issue, al tomarse, recibe su propio plan detallado vía `superpowers:writing-plans` + ejecución `subagent-driven-development`, igual que el resto del roadmap.

**Cobertura verificada:** los 10 RF 🟡 Parcial (RF-05, 07b, 09, 11, 13, 14, 17, 19, 20, 28) y los 3 RF 🔴 Incorrecto (RF-10, 15b, 16) quedan cerrados por los ítems de abajo — el mapeo explícito está en la columna "RF" de cada tabla. RF-14 y RF-17 no generan un ítem propio: quedan resueltos como efecto directo de AUD-24 (arregla el insumo de RF-17) y AUD-30 (le da a RF-14 la capacidad propia que le faltaba) — están marcados como "cierra junto con" en vez de listados aparte, para no duplicar trabajo.

---

## Grupo 1 — Core, empleados y seguridad

- [x] **AUD-01** — `fix(employee): marcar estado_liquidacion=PENDIENTE al crear un adelanto` (`fix/employee-estado-liquidacion-pendiente`)
  - RF: RF-07b (🟡→✅) · Severidad: Alto
  - Qué implica: en `AdvanceService.create()`, marcar `employee.estadoLiquidacion = PENDIENTE` si no lo está ya. Test de integración: crear adelanto → `GET /employees/{id}` → `estadoLiquidacion=PENDIENTE`. El cierre de período (`PayrollPeriodCloseService.markEmployeeUpToDate`) ya vuelve a poner `AL_DIA` correctamente, esa mitad no se toca.
  - Archivos/paquetes: `backend/advance/AdvanceService.java`, `backend/employee/EmployeeService.java` (si hace falta un método compartido)
  - Depende de: nada

- [x] **AUD-02** — `fix(employee): bloquear branchIds vacío al editar un empleado` (`fix/employee-branchids-vacio`)
  - RF: RF-05 (🟡→✅) · Severidad: Medio
  - Qué implica: `EmployeeService.update()` acepta hoy `branchIds: []` y deja al empleado sin sucursal. Agregar el mismo `@NotEmpty` (o chequeo explícito) que ya protege la creación.
  - Archivos/paquetes: `backend/employee/dto/UpdateEmployeeRequest.java`, `backend/employee/EmployeeService.java`
  - Depende de: nada

- [x] **AUD-03** — `fix(employee): EmployeeService.isInScope reusa EmployeeResolver` (`fix/employee-scope-defensa`)
  - Severidad: Medio/Riesgoso
  - Qué implica: `isInScope` trata cualquier rol que no sea SUPERVISOR como "ve cualquier empleado" — sin defensa propia contra `Rol.EMPLOYEE`, a diferencia de `EmployeeResolver` que sí la tiene. Reusar `EmployeeResolver` en vez de mantener una segunda implementación del mismo scoping. De paso, unificar el patrón de `/employees/{id}` (bloqueo por rol en `@PreAuthorize`) con el de `/users/{id}` (resuelto en runtime dentro del service) — hoy son estilísticamente distintos, ambos correctos, vale la pena converger a uno solo.
  - Archivos/paquetes: `backend/employee/EmployeeService.java`, `backend/employee/EmployeeResolver.java`
  - Depende de: nada

- [x] **AUD-04** — `fix(security): revalidar rol y sucursales en cada refresh de JWT` (`fix/security-refresh-revalida-rol`)
  - Severidad: Alto
  - Qué implica: `AuthService.refresh()` reconstruye el principal desde los claims del refresh token viejo. Reconstruirlo desde la fila actual de `User` (rol, `branch_ids`) en cada refresh, no solo revalidar estado activo/inactivo (eso ya lo hace SEC-1).
  - Archivos/paquetes: `backend/security/AuthService.java`
  - Depende de: nada

- [x] **AUD-05** — `fix(security): enforcear debeCambiarPassword en backend` (`fix/security-force-password-change-backend`)
  - RF: RF-01 (endurece lo ya ✅) · Severidad: Medio
  - Qué implica: hoy el flag solo se enforcea en el guard de Angular (client-side routing). Incluirlo en el JWT (o resolverlo por request) y rechazar en backend cualquier endpoint que no sea `/auth/change-password`/`/auth/logout` mientras esté en `true`.
  - Archivos/paquetes: `backend/security/JwtService.java`, `backend/security/SecurityConfig.java`
  - Depende de: nada

- [x] **AUD-06** — `fix(db): índice company_id en branch y app_user` (`fix/db-index-company-id`)
  - Severidad: Medio (RNF-01)
  - Qué implica: nueva migración Flyway con `CREATE INDEX` sobre `company_id` en ambas tablas, siguiendo el mismo patrón ya usado desde V6 en adelante.
  - Archivos/paquetes: nueva migración en `backend/src/main/resources/db/migration/`
  - Depende de: nada

## Grupo 2 — Disponibilidad, horarios y feriados

- [x] **AUD-07** — `fix(schedule): bloquear turno sobre licencia aprobada + bloque visual de licencia en el builder` (`fix/schedule-bloquea-licencia-aprobada`)
  - RF: RF-15e (❌→✅), aporta a RF-13 (🟡→✅) · Severidad: Crítico
  - Qué implica: `ScheduleService.create()`/`update()` no consultan `LeaveRequest` — agregar chequeo de solapamiento contra licencias `APROBADA` del empleado (misma semántica de rango que ya usa `LeaveRequestService.approve()`), 409 si hay conflicto. En el frontend, `schedule-builder.component.ts` no importa nada de licencias — agregar el bloque con estilo diferenciado que pide `ux-decisions.md` #1, sin permitir asignar turno encima.
  - Archivos/paquetes: `backend/schedule/ScheduleService.java`, `backend/leave/LeaveRequestRepository.java` (nuevo método), `frontend/features/schedules/components/schedule-builder/`
  - Depende de: nada

- [x] **AUD-08** — `fix(schedule): advertencia de disponibilidad visible en lectura y al crear` (`fix/schedule-warning-visible`)
  - RF: RF-10 (🔴→✅), aporta a RF-13 (🟡→✅) · Severidad: Alto
  - Qué implica: dos bugs encadenados. Backend: `list()`/`findById()` usan el overload de `ScheduleResponse.from()` que fuerza `warning=null` — recalcular `checkDisponibilidad()` también en lectura. Frontend: `handleCreateSubmit()` descarta la respuesta de `create()` y recarga la lista completa en vez de usarla — usar la respuesta directa para feedback inmediato, además de que la recarga ya funcione una vez resuelto el lado backend.
  - Archivos/paquetes: `backend/schedule/ScheduleService.java`, `backend/schedule/dto/ScheduleResponse.java`, `frontend/features/schedules/components/schedule-builder/schedule-builder.component.ts`
  - Depende de: nada

- [x] **AUD-09** — `feat(schedule): completar schedule builder — editar, confirmar, cambiar estado y duplicar semanal` (`feat/schedule-builder-completo`)
  - RF: RF-11 (🟡→✅), aporta a RF-13 (🟡→✅) · Severidad: Alto
  - Qué implica: el backend expone y testea `PATCH /schedules/{id}`, `POST /schedules/{id}/confirm`, `PATCH /schedules/{id}/status`, `POST /schedules/{id}/duplicate-weekly` — el frontend solo tiene `list()`/`create()`/`delete()`. Agregar los 4 métodos al servicio Angular y la UI correspondiente (modal de edición, botones de acción, flujo de duplicar como plantilla semanal).
  - Archivos/paquetes: `frontend/features/schedules/services/schedule.service.ts`, `frontend/features/schedules/components/schedule-builder/`
  - Depende de: nada (en paralelo con AUD-07/08, mismo componente — conviene coordinarlos en una sola rama si se ejecutan cerca en el tiempo)

- [x] **AUD-10** — `feat(availability): integrar disponibilidad declarada al armado de horarios` (`feat/schedule-availability-integrada`)
  - RF: RF-09 (🟡→✅) · Severidad: Medio
  - Qué implica: la disponibilidad es consultable y está bien scopeada por rol, pero no está integrada al flujo de armado — al seleccionar un empleado en el modal de "nuevo turno", mostrar su disponibilidad del día como referencia visual (no bloqueante, RF-10 ya cubre la advertencia).
  - Archivos/paquetes: `frontend/features/schedules/components/schedule-builder/`, `frontend/features/availability/services/availability.service.ts`
  - Depende de: AUD-09 (mismo componente, conviene ir después de tener la edición completa)

- [x] **AUD-11** — `fix(holiday): feriados recurrentes deben repetirse por (mes, día) sin importar el año` (`fix/holiday-recurrente`)
  - Severidad: Bajo (impacto silencioso en nómina — subliquidación de feriados trabajados en años posteriores)
  - Qué implica: `recurrente=true` se persiste pero ninguna query hace matching por `(mes, día)` ignorando el año. Agregar esa comparación en `HolidayRepository` y replicarla en `HoursCalculationStrategySelector`.
  - Archivos/paquetes: `backend/holiday/HolidayRepository.java`, `backend/payroll/strategy/HoursCalculationStrategySelector.java`
  - Depende de: nada

- [x] **AUD-12** — `fix(schedule): pulido del modal de detalle — label de medianoche con día + labels de estado/tipo` (`fix/schedule-detalle-pulido`)
  - Severidad: Bajo
  - Qué implica: el label de continuación de medianoche no incluye el nombre del día (pide `ux-decisions.md` #1: "continúa de [día] [hora]hs"). El modal de detalle muestra `estado`/`tipoTurno` crudos en vez de `ESTADO_TURNO_LABELS`/`TIPO_TURNO_LABELS`, ya declarados en `strings.ts`.
  - Archivos/paquetes: `frontend/features/schedules/components/schedule-builder/schedule-builder.component.ts`, `.html`
  - Depende de: nada

- [x] **AUD-13** — `test(schedule): regresión de timestamp sin segundos (issue #101)` (`test/schedule-timestamp-sin-segundos`)
  - Severidad: Bajo
  - Qué implica: el fix del #101 es correcto (confirmado por análisis del parser JSR-310) pero no tiene ningún test que lo proteja de romperse si cambia la configuración de Jackson a futuro.
  - Archivos/paquetes: `backend/src/test/java/com/staffly/backend/schedule/ScheduleControllerTest.java`
  - Depende de: nada

## Grupo 3 — Licencias

- [x] **AUD-14** — `fix(leaves): corregir contrato de LeaveRequestResponse — nombre de empleado y tipo de licencia` (`fix/leaves-contrato-response`)
  - Severidad: Crítico
  - Qué implica: el backend expone `leaveTypeNombre` y ningún nombre de empleado; el frontend espera `employeeNombre`/`employeeApellido`/`leaveTypeName`, que llegan `undefined` siempre. Agregar `employeeNombre`/`employeeApellido` a `LeaveRequestResponse.from()` (el query ya hace `JOIN FETCH` del empleado) y alinear el nombre de `leaveTypeNombre`.
  - Archivos/paquetes: `backend/leave/dto/LeaveRequestResponse.java`, `frontend/features/leaves/models/leave.ts`
  - Depende de: nada

- [x] **AUD-15** — `fix(leaves): alinear cupo anual de LeaveType (issue #102)` (`fix/leaves-cupo-anual-contrato`)
  - RF: RF-15b (🔴→✅) · Severidad: Alto
  - Qué implica: backend modela un único `cupoAnual: Integer` nullable; frontend espera `tieneCupoAnual`+`cuposDiasAnual`, campos que no existen en el backend — el valor configurado se guarda siempre `null`, sin error visible. Alinear el frontend a `cupoAnual: number | null`, manteniendo el checkbox solo como control de UI.
  - Archivos/paquetes: `frontend/features/leaves/models/leave.ts`, `frontend/features/leaves/components/leaves-list/leaves-list.component.ts`, `.html`
  - Depende de: nada

- [x] **AUD-16** — `fix(leaves): ocultar botón Rechazar en licencias ya APROBADA (issue #103)` (`fix/leaves-rechazar-en-aprobada`)
  - Severidad: Medio
  - Qué implica: el backend bloquea correctamente el reject desde `APROBADA` (400), pero el template del frontend sigue ofreciendo el botón en ese estado. Eliminar el bloque condicional correspondiente.
  - Archivos/paquetes: `frontend/features/leaves/components/leaves-list/leaves-list.component.html`
  - Depende de: nada

- [x] **AUD-17** — `fix(leaves): bug de timezone UTC/local en canCancel() (issue #104)` (`fix/leaves-timezone-cancel`)
  - Severidad: Medio
  - Qué implica: `new Date().toISOString().split('T')[0]` da la fecha en UTC, no local — puede ocultar el botón "Cancelar" para una licencia que todavía no empezó, en tenants con offset negativo. Calcular la fecha local con componentes (`getFullYear()`/`getMonth()`/`getDate()`) en vez de pasar por UTC.
  - Archivos/paquetes: `frontend/features/leaves/components/leaves-list/leaves-list.component.ts`
  - Depende de: nada

- [x] **AUD-18** — `feat(leaves): agregar campo aprobado_por a LeaveRequest` (`feat/leaves-aprobado-por`)
  - Severidad: Medio
  - Qué implica: pedido explícitamente por el modelo de entidad del documento de requerimientos; no existe en ningún nivel hoy. Nueva columna + campo + set en `approve()` con el `userId` del principal autenticado + exponerlo en la respuesta.
  - Archivos/paquetes: nueva migración Flyway, `backend/leave/LeaveRequest.java`, `LeaveRequestService.java`, `dto/LeaveRequestResponse.java`
  - Depende de: nada

- [x] **AUD-19** — `feat(leaves): detalle real del conflicto en el 409 de approve + consumo en frontend` (`feat/leaves-conflicto-detallado`)
  - Severidad: Medio
  - Qué implica: hoy el 409 trae solo IDs de turnos en conflicto, sin fecha/hora/sucursal, y el frontend ni los usa (muestra un mensaje fijo genérico). Incluir objetos con fecha/hora/sucursal en el error y consumirlos en el mensaje del frontend.
  - Archivos/paquetes: `backend/leave/LeaveApprovalConflictException.java`, `backend/common/GlobalExceptionHandler.java`, `frontend/features/leaves/components/leaves-list/leaves-list.component.ts`
  - Depende de: nada

- [x] **AUD-20** — `feat(leaves): advertencia proactiva de conflicto en la card (ux-decisions #4)` (`feat/leaves-advertencia-proactiva`)
  - Severidad: Medio
  - Qué implica: hoy el usuario se entera del conflicto recién al hacer click en "Aprobar" — el documento de decisiones de UX pide anticiparlo en la card. Exponer un campo `tieneConflicto` en el listado de solicitudes pendientes (misma query que ya usa `approve()`), pintarlo en el frontend.
  - Archivos/paquetes: `backend/leave/dto/LeaveRequestResponse.java`, `LeaveRequestService.java`, `frontend/features/leaves/components/leaves-list/leaves-list.component.html`
  - Depende de: AUD-19 (comparten la misma zona del contrato de conflicto, conviene resolverlos juntos)

- [x] **AUD-21** — `feat(leaves): implementar el camino "decide igual" del flujo de conflicto` (`feat/leaves-decide-igual`)
  - Severidad: Medio/Alto
  - Qué implica: **requiere una decisión de producto antes de codear** — el diagrama de `flujos-proceso.md` #1 modela una rama de "decide igual" tras el 409 que hoy no tiene ninguna implementación (la única salida es borrar el turno en conflicto desde otro módulo, y solo si sigue `PLANIFICADO`). Definir si se implementa como flag `force=true` explícito en `approve()`, o como link directo desde la card al turno en conflicto para resolverlo sin perder contexto — cualquiera de las dos cierra la ambigüedad, la actual no.
  - Archivos/paquetes: `backend/leave/LeaveRequestController.java`, `LeaveRequestService.java`, frontend `leaves-list.component.ts`/`.html`
  - Depende de: decisión de producto (bloqueante antes de implementar, no antes de crear el issue)

- [x] **AUD-22** — `fix(leaves): limpiar validación de fechas redundante` (`fix/leaves-validacion-fechas-redundante`)
  - Severidad: Bajo
  - Qué implica: dos condiciones consecutivas lógicamente idénticas para `LocalDate` en la validación de alta de solicitud — sin impacto funcional, solo legibilidad.
  - Archivos/paquetes: `backend/leave/LeaveRequestService.java`
  - Depende de: nada

- [x] **AUD-23** — `test(leaves): cobertura faltante — backend (#103/#104) y suite completa de frontend` (`test/leaves-cobertura`)
  - Severidad: Medio
  - Qué implica: faltan tests backend para reject-desde-aprobada y cancel-con-fecha-inicio-hoy (comportamiento correcto, pero no fijado). `features/leaves/` tiene 0% de cobertura de frontend — sin ese test, un mock con la forma real de `LeaveRequestResponse` habría detectado AUD-14 de inmediato. Agregar specs mínimos: render de card contra el contrato real, y `canCancel()` con distintos estados/fechas.
  - Archivos/paquetes: `backend/src/test/java/com/staffly/backend/leave/LeaveRequestControllerTest.java`, nuevos `*.spec.ts` en `frontend/features/leaves/`
  - Depende de: AUD-14, AUD-17 (los specs de frontend deberían escribirse contra el contrato ya corregido)

## Grupo 4 — Nómina y liquidación

- [x] **AUD-24** — `fix(payroll): umbral de hora extra por día/semana, no por período completo` (`fix/payroll-umbral-hora-extra`)
  - RF: RF-16 (🔴→✅), cierra junto con RF-17 (🟡→✅, el insumo que lo volvía parcial) · Severidad: Crítico
  - Qué implica: el umbral se acumula hoy sobre todo el período (`horasContadas` nunca se resetea); con el default de fábrica (`umbral=8` + `MENSUAL`) esto sobrepaga horas extra 40-48% desde el primer cierre. Mínimo: default coherente con `MENSUAL`, o exigir configuración explícita antes del primer cierre. Ideal: campo `tipoUmbral` (DIARIO/SEMANAL) en `PayrollConfig`, agrupar turnos por día/semana antes de aplicar la estrategia.
  - Archivos/paquetes: `backend/payroll/strategy/OvertimeStrategy.java`, `backend/payslip/builder/PayslipBuilder.java`, `backend/payroll/PayrollConfig.java` (+migración si se agrega `tipoUmbral`), `PayrollConfigService.java`
  - Depende de: nada — **este es el hallazgo de mayor impacto financiero del informe, priorizarlo primero dentro del grupo**

- [x] **AUD-25** — `fix(payslip): persistir y mostrar el desglose monetario por categoría de hora` (`fix/payslip-desglose-monetario`)
  - RF: aporta a RNF-06 · Severidad: Alto
  - Qué implica: el PDF muestra el bruto total del período junto a "Horas normales" y deja "Horas extra"/"Horas feriado" sin importe; la card web muestra el sueldo base completo en el mismo lugar. Agregar `montoHorasNormales/Extra/Feriado` a `Payslip`/`PayslipResponse` (calculables en el Builder: `totalNormal × valorHora`, etc.), usarlos en PDF y card.
  - Archivos/paquetes: `backend/payslip/builder/PayslipCalculation.java`, `backend/payslip/Payslip.java` (+migración), `dto/PayslipResponse.java`, `backend/payslip/pdf/OpenPdfPayslipAdapter.java`, `frontend/features/payslips/components/payslips-list/payslips-list.component.html`
  - Depende de: AUD-24 (mismo archivo `PayslipBuilder.java`, conviene secuenciar para no pisarse)

- [x] **AUD-26** — `fix(payroll): lock en el cierre de período + evitar Payslips duplicados al reabrir` (`fix/payroll-cierre-lock-duplicados`)
  - RF: aporta a RF-20 (🟡→✅) · Severidad: Crítico (fusiona los hallazgos de concurrencia y de reapertura, misma zona de riesgo)
  - Qué implica: dos problemas de la misma familia. (a) `validateAndLoadPeriod` es check-then-act puro, sin `@Lock`/`SELECT FOR UPDATE` ni `UNIQUE` de respaldo en `payslip` — dos cierres casi simultáneos pueden duplicar recibos. (b) `reopen()` no revierte `Advance` a `PENDIENTE` y `persistPayslip` nunca verifica si ya existe un Payslip para ese empleado+período antes de insertar — reabrir y volver a cerrar (el flujo que la propia UI sugiere) deja 2 recibos `NORMAL` para el mismo período. Arreglar ambos juntos: `@Lock(PESSIMISTIC_WRITE)` en la lectura del período al cerrar, `UNIQUE(company_id, employee_id, payroll_period_id)` en `payslip`, y `persistPayslip` actualiza si ya existe en vez de insertar.
  - Archivos/paquetes: `backend/payroll/PayrollPeriodCloseService.java`, `PayrollPeriodService.java`, nueva migración
  - Depende de: nada

- [x] **AUD-27** — `fix(payroll): alinear rol habilitado para cerrar un período en las 3 capas` (`fix/payroll-rol-cierre-periodo`)
  - RF: aporta a RF-20 (🟡→✅) · Severidad: Crítico
  - Qué implica: `api-design.md` dice ADMIN+RRHH, el backend es ADMIN-only (`@PreAuthorize("hasRole('ADMIN')")`, probable copy-paste del endpoint `reopen` vecino), el frontend muestra el botón a RRHH igual — que recibe 403 con un mensaje engañoso de "reintentá". Decidir el comportamiento correcto y alinear backend/frontend/documento — si se decide ADMIN-only, ocultar el botón a RRHH y corregir el doc; si se decide ADMIN+RRHH, cambiar el `@PreAuthorize`.
  - Archivos/paquetes: `backend/payroll/PayrollPeriodController.java`, `frontend/features/payroll/components/payroll-periods/`, `docs/api-design.md`
  - Depende de: decisión de producto (bloqueante antes de implementar, no antes de crear el issue)

- [x] **AUD-28** — `fix(payroll): mover @Transactional al método close() para garantizar atomicidad` (`fix/payroll-transactional-close`)
  - Severidad: Alto (marcado "Requiere prueba" en el informe — confirmar antes de cerrar el issue)
  - Qué implica: `@Transactional` está a nivel de clase en `PayrollPeriodCloseService`, pero el método `close()` concreto vive en la clase abstracta padre (`PayrollCloseTemplate`) y nunca se sobreescribe — riesgo real de que el proxy de Spring no envuelva la ejecución completa. Mover la anotación directamente al método `close()`. Antes de dar el issue por cerrado, agregar el test que fuerza una excepción a mitad del loop y confirma rollback completo.
  - Archivos/paquetes: `backend/payroll/PayrollCloseTemplate.java`, `PayrollPeriodCloseService.java`
  - Depende de: nada

- [x] **AUD-29** — `feat(advances): exponer historial propio de adelantos al EMPLOYEE` (`feat/advances-propio-employee`)
  - RF: RF-19 (🟡→✅) · Severidad: Medio
  - Qué implica: el backend ya soporta y testea que un EMPLOYEE vea sus propios adelantos (RF-29 aplicado a este dato), pero `advances-list.component.ts` bloquea la carga para cualquier rol que no sea ADMIN/RRHH y muestra "consultá con tu encargado". Replicar el patrón que ya funciona en `PayslipsListComponent`.
  - Archivos/paquetes: `frontend/features/advances/components/advances-list/`
  - Depende de: nada

## Grupo 5 — Reportes (módulo nuevo)

- [x] **AUD-30** — `feat(reports): GET /reports/hours-worked` (`feat/report-hours-worked`)
  - RF: RF-22 (❌→✅), cierra junto con RF-14 (🟡→✅, la capacidad propia que le faltaba a `schedule/`) · Severidad: Crítico
  - Qué implica: no existe `backend/report/` — confirmado en vivo contra `/v3/api-docs`. El frontend (`features/reports/`) ya está construido contra el contrato de `api-design.md` sección 14 y pega a rutas que no existen. Implementar el endpoint sobre `Schedule` (contando solo turnos `CUMPLIDO`, mismo filtro que ya usa `PayslipBuilder` — no reinventar el criterio), con RBAC ADMIN/RRHH y filtros `?branchId=&desde=&hasta=`.
  - Archivos/paquetes: nuevo `backend/report/` (entidad de respuesta, service, controller)
  - Depende de: AUD-24 (no tiene sentido reportar horas extra hasta que el cálculo esté arreglado)

- [x] **AUD-31** — `feat(reports): GET /reports/payroll-cost` (`feat/report-payroll-cost`)
  - RF: RF-23 (❌→✅) · Severidad: Crítico
  - Qué implica: mismo patrón que AUD-30, sobre `Payslip` cerrado, agrupado por sucursal/empresa/período.
  - Archivos/paquetes: `backend/report/`
  - Depende de: AUD-30 (mismo paquete nuevo, conviene que exista la base antes)

- [x] **AUD-32** — `feat(reports): GET /reports/pending-advances` (`feat/report-pending-advances`)
  - RF: RF-24 (❌→✅) · Severidad: Crítico
  - Qué implica: mismo patrón, sobre `Advance` con `estado=PENDIENTE`.
  - Archivos/paquetes: `backend/report/`
  - Depende de: AUD-30

- [x] **AUD-33** — `feat(reports): exportación PDF/CSV` (`feat/report-export`)
  - RF: RF-25 (❌→✅) · Severidad: Alto
  - Qué implica: `GET /reports/{report}/export?format=pdf|csv`, reusando `PdfExportAdapter` (interfaz genérica ya confirmada real, hoy con una sola implementación en `payslip/`) para PDF; CSV es serialización simple.
  - Archivos/paquetes: `backend/report/`, `backend/common/pdf/`
  - Depende de: AUD-30, AUD-31, AUD-32 (necesita los 3 reportes para exportar algo)

## Grupo 6 — Auditoría

- [ ] **AUD-34** — `feat(audit): AuditLog completo — eventos de Advance/Payslip/LeaveRequest + GET /audit-log` (`feat/audit-log-completo`)
  - RF: RF-28 (🟡→✅) · Severidad: Crítico (fusiona el endpoint faltante con los eventos faltantes — no tiene sentido construir uno sin el otro)
  - Qué implica: `common/audit/` tiene entidad + Observer + repo, pero sin controller — confirmado en vivo. Solo 2 de las 5 entidades esperadas (Employee, Schedule) publican eventos; `Advance`, `Payslip` (incluida la anulación de un recibo pagado, la operación más sensible del sistema) y `LeaveRequest` no publican ninguno. Antes de codear: **acordar la forma de respuesta** del endpoint con el frontend (`features/audit/models/audit-log.ts` hoy espera `accion`/`descripcion`/`userName`/`cambios`, que no coincide con la entidad real fila-por-campo — decidir si se agrupa por evento o se expone fila-por-campo y se ajusta el frontend). Luego: publicar el evento en `AdvanceService.create/delete`, `PayslipService.voidAndAdjust/markPaid`, `LeaveRequestService.approve/reject/cancel`, y construir el controller con los filtros de `api-design.md` sección 15.
  - Archivos/paquetes: `backend/common/audit/` (nuevo controller), `backend/advance/AdvanceService.java`, `backend/payslip/PayslipService.java`, `backend/leave/LeaveRequestService.java`, `frontend/features/audit/models/audit-log.ts`
  - Depende de: decisión de forma de respuesta (bloqueante antes de implementar, no antes de crear el issue)

## Grupo 7 — Documentación y limpieza menor

- [ ] **AUD-35** — `docs(api-design): documentar DELETE /leave-types/{id} y el alcance EMPLOYEE de GET /advances` (`docs/api-design-endpoints-faltantes`)
  - Severidad: Bajo
  - Qué implica: dos endpoints existen en código, correctamente restringidos, pero no están en el documento — agregarlos a las secciones 9 y 12 de `api-design.md`.
  - Archivos/paquetes: `docs/api-design.md`
  - Depende de: nada

- [ ] **AUD-36** — `chore(payroll): resolver código y estado inalcanzable` (`chore/payroll-codigo-inalcanzable`)
  - Severidad: Bajo
  - Qué implica: `StandardHoursStrategy` (100% cubierta por tests unitarios directos, nunca seleccionada en el flujo real) y `EstadoAdelanto.CANCELADO` (definido en el enum, sin ningún endpoint que lo asigne) son deuda menor. Decidir por ítem: implementar el camino que los usa (ej. `PATCH /advances/{id}/cancel`), o eliminarlos si de verdad no hacen falta — no dejarlos como código muerto sin decisión.
  - Archivos/paquetes: `backend/payroll/strategy/StandardHoursStrategy.java`, `backend/advance/EstadoAdelanto.java`
  - Depende de: nada

---

## Resumen

| Grupo | Ítems | Crítico | Alto | Medio | Bajo |
|---|---|---|---|---|---|
| 1 — Core, empleados, seguridad | 6 | 0 | 2 | 3 | 0 |
| 2 — Disponibilidad, horarios, feriados | 7 | 1 | 2 | 1 | 3 |
| 3 — Licencias | 10 | 1 | 1 | 6 | 2 |
| 4 — Nómina y liquidación | 6 | 3 | 2 | 1 | 0 |
| 5 — Reportes | 4 | 3 | 1 | 0 | 0 |
| 6 — Auditoría | 1 | 1 | 0 | 0 | 0 |
| 7 — Documentación y limpieza | 2 | 0 | 0 | 0 | 2 |
| **Total** | **36** | **9** | **8** | **11** | **7** |

Los 36 ítems cubren los 40 hallazgos del informe (algunos se fusionaron por compartir archivo/zona de riesgo — H-3+H-5 en AUD-26, el endpoint+eventos de auditoría en AUD-34, los dos gaps de documentación en AUD-35) y los 13 RF en estado Parcial/Incorrecto de la matriz.

**3 ítems requieren una decisión de producto antes de implementarse** (no antes de crear el issue): AUD-21 (camino "decide igual" en conflicto de licencias), AUD-27 (rol habilitado para cerrar período), AUD-34 (forma de respuesta del audit log). Vale la pena resolver esas 3 decisiones primero, en paralelo a que se creen y empiecen los demás issues.
