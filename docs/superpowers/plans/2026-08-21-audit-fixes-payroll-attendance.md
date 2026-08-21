# Plan de correccion post-auditoria: empleados, turnos y nomina

Fecha: 2026-08-21

## Objetivo

Corregir los riesgos detectados en la auditoria funcional del proyecto Staffly, priorizando los flujos de empleados, horarios/jornadas, asistencias representadas por estado de turno, adelantos, cierre de periodos, recibos y liquidacion de sueldos.

La fuente de verdad sigue siendo:

- `docs/requerimientos-sistema-gestion-personal.md`
- `docs/flujos-proceso.md`
- `docs/api-design.md`
- `docs/roadmap.md`

Nota de alcance: el fichaje real por check-in/check-out o biometria esta explicitamente fuera de alcance de v1. La asistencia v1 se representa con `Schedule.estado`: `PLANIFICADO`, `CONFIRMADO`, `CUMPLIDO`, `AUSENTE`.

## Estado inicial verificado

- Frontend: `npm run build` paso. `npm test -- --watch=false` paso con 155 tests en 29 archivos.
- Backend: reportes Surefire registran 358 tests en 35 clases, sin fallos. La suite completa en una sola corrida no termino dentro del timeout por lentitud/logs, por lo que debe optimizarse o ejecutarse en CI con timeout suficiente.
- El sistema compila y gran parte del alcance esta implementado, pero hay desalineaciones de negocio en nomina/liquidacion que impiden considerarlo listo para produccion.

## Principios de correccion

1. Preservar historial: no borrar ni sobrescribir informacion contable relevante.
2. Hacer que los estados representen hechos reales: generar un recibo no equivale a pagarlo.
3. Mantener liquidaciones reproducibles: un recibo debe poder explicar que adelantos, horarios, licencias y reglas uso.
4. Aislar permisos por tenant, rol y sucursal tambien en cambios de destino, no solo en lectura.
5. Cubrir cada correccion con tests de regresion que fallen con la implementacion actual.

## Plan priorizado

### P0 - Correcciones bloqueantes de liquidacion

#### Issue 1: Baja laboral debe marcar liquidacion pendiente e incluir al empleado en cierre

Severidad: Critico

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/employee/EmployeeService.java`
- `backend/src/main/java/com/staffly/backend/employee/EmployeeRepository.java`
- `backend/src/test/java/com/staffly/backend/employee/EmployeeControllerTest.java`
- `backend/src/test/java/com/staffly/backend/payroll/PayrollPeriodCloseControllerTest.java`

Problema:

`PATCH /employees/{id}/status` puede pasar un empleado a `BAJA` sin marcar `estadoLiquidacion = PENDIENTE`. El cierre de nomina solo incluye empleados activos o `BAJA + PENDIENTE`, por lo que un empleado dado de baja antes del cierre podria quedar sin liquidacion final.

Comportamiento esperado:

Al pasar a `BAJA`, el sistema debe marcar `estadoLiquidacion = PENDIENTE` cuando haya un periodo abierto/reabierto, adelantos pendientes, horarios cumplidos o cualquier otro saldo liquidable. La baja no debe bloquearse.

Implementacion recomendada:

- Agregar una politica de estado de liquidacion en `EmployeeService.updateStatus`.
- Consultar si existen periodos abiertos/reabiertos o saldos pendientes antes de decidir.
- Mantener `AL_DIA` solo si no hay nada liquidable.
- Agregar tests para baja antes del cierre y cierre posterior.

Validacion:

- Crear empleado activo con turno cumplido en periodo abierto.
- Pasarlo a `BAJA`.
- Cerrar periodo.
- Verificar que se genera payslip y luego el empleado queda `AL_DIA`.

#### Issue 2: Adelantos deben imputarse a periodo y no descontarse fuera de rango

Severidad: Critico

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/advance/Advance.java`
- `backend/src/main/resources/db/migration/V14__create_advance.sql`
- `backend/src/main/java/com/staffly/backend/payroll/PayrollPeriodCloseService.java`
- `backend/src/main/java/com/staffly/backend/payroll/PayrollPeriodService.java`
- `backend/src/main/java/com/staffly/backend/report/PendingAdvancesReportService.java`

Problema:

`Advance` no tiene `payrollPeriodId` ni otra imputacion historica. El cierre descuenta todos los adelantos `PENDIENTE` del empleado, aunque sean posteriores al periodo cerrado.

Comportamiento esperado:

Cada adelanto debe descontarse en la liquidacion que corresponda y quedar historicamente asociado al periodo o recibo que lo desconto.

Implementacion recomendada:

- Agregar `payroll_period_id` nullable o una tabla/vinculo explicito de aplicacion.
- Definir regla v1: si no se carga periodo explicitamente, se descuenta en el primer periodo abierto/cerrado cuyo `fechaFin >= advance.fecha`.
- En el cierre, filtrar por periodo aplicable.
- Al reabrir, revertir solo adelantos aplicados por ese periodo.
- Ajustar reportes para distinguir pendientes reales de futuros no imputados.

Validacion:

- Adelanto con fecha dentro del periodo se descuenta.
- Adelanto con fecha posterior al `fechaFin` no se descuenta.
- Reopen revierte solo adelantos del periodo reabierto.

#### Issue 3: Cierre de nomina debe generar recibos en estado GENERADO

Severidad: Alto

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/payroll/PayrollPeriodCloseService.java`
- `backend/src/main/java/com/staffly/backend/payslip/PayslipService.java`
- `backend/src/test/java/com/staffly/backend/payroll/PayrollPeriodCloseControllerTest.java`
- `backend/src/test/java/com/staffly/backend/payslip/PayslipControllerTest.java`

Problema:

El cierre de periodo guarda recibos como `PAGADO` y setea `fechaPago`. Esto contradice el flujo separado `GENERADO -> PAGADO` expuesto por `PATCH /payslips/{id}/mark-paid`.

Comportamiento esperado:

El cierre calcula y genera recibos en `GENERADO`. El pago real debe registrarse con `mark-paid`.

Implementacion recomendada:

- Remover `setEstado(PAGADO)` y `setFechaPago(LocalDate.now())` del cierre.
- Asegurar que recierres tras reapertura recalculen el snapshot sin marcar como pagado.
- Actualizar tests que hoy esperan `PAGADO`.
- Revisar UI para que muestre accion "Marcar pagado" despues del cierre.

Validacion:

- Cerrar periodo crea payslips `GENERADO`.
- `mark-paid` pasa recibo a `PAGADO` y registra `fechaPago`.
- `mark-paid` sobre `PAGADO` sigue fallando.

### P1 - Correcciones importantes de integridad y permisos

#### Issue 4: Anulacion y ajuste deben recalcular preservando adelantos aplicados

Severidad: Alto

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/payslip/PayslipService.java`
- `backend/src/main/java/com/staffly/backend/payslip/PayslipFactory.java`
- `backend/src/test/java/com/staffly/backend/payslip/PayslipVoidControllerTest.java`

Problema:

Al anular un recibo pagado, el ajuste recalcula usando adelantos `PENDIENTE`. Los adelantos ya descontados por el recibo original estan `DESCONTADO`, por lo que quedan fuera del ajuste.

Comportamiento esperado:

El ajuste debe ser reproducible y consistente con el recibo original. Debe considerar adelantos aplicados originalmente y, si el producto lo permite, nuevos adelantos pendientes aplicables segun regla explicita.

Implementacion recomendada:

- Reconstruir adelantos desde `original.adelantosAplicados`.
- Definir si el ajuste incluye nuevos adelantos pendientes o solo corrige el recibo original.
- Guardar en el ajuste la lista final de adelantos considerados.
- Agregar tests de anulacion con adelanto previamente descontado.

Validacion:

- Recibo original con adelanto aplicado se anula.
- Ajuste conserva o explica correctamente ese adelanto.
- El neto ajustado no omite descuentos historicos.

#### Issue 5: Corregir contrato frontend/backend de motivoAnulacion

Severidad: Medio

Ubicaciones iniciales:

- `frontend/src/app/features/payslips/models/payslip.ts`
- `frontend/src/app/features/payslips/components/payslips-list/payslips-list.component.ts`
- `backend/src/main/java/com/staffly/backend/payslip/dto/VoidPayslipRequest.java`
- `backend/src/main/java/com/staffly/backend/payslip/PayslipController.java`

Problema:

El frontend envia `{ motivo }`, pero backend espera `motivoAnulacion`. El motivo puede guardarse como `null`, perdiendo trazabilidad.

Comportamiento esperado:

El motivo de anulacion debe ser obligatorio, validado y persistido.

Implementacion recomendada:

- Cambiar modelo frontend a `motivoAnulacion`.
- Enviar `{ motivoAnulacion }`.
- Agregar `@Valid` en controller.
- Agregar `@NotBlank @Size(max = 1000)` en request backend.
- Cubrir con tests FE y BE.

Validacion:

- UI envia motivo correcto.
- Backend rechaza motivo vacio.
- Recibo anulado conserva `motivoAnulacion`.

#### Issue 6: Supervisores no pueden crear ni mover turnos a sucursales fuera de alcance

Severidad: Alto

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/schedule/ScheduleService.java`
- `backend/src/test/java/com/staffly/backend/schedule/ScheduleControllerTest.java`

Problema:

El servicio valida que el supervisor pueda ver al empleado o el turno actual, pero no valida que el `branchId` destino pertenezca a sus sucursales.

Comportamiento esperado:

Un supervisor solo puede crear, actualizar, confirmar o eliminar turnos dentro de sus sucursales asignadas.

Implementacion recomendada:

- Agregar helper `resolveBranchForCaller`.
- Validar `branchId` en create.
- Validar branch destino en update antes de `setBranch`.
- Mantener respuesta 404 para recursos fuera de scope.

Validacion:

- Supervisor de sucursal A no crea turno en sucursal B.
- Supervisor de sucursal A no mueve turno a sucursal B.
- Admin/RRHH siguen pudiendo operar todas las sucursales de la empresa.

### P2 - Correcciones funcionales y de consistencia

#### Issue 7: Resolver periodicidad SEMANAL end-to-end

Severidad: Medio

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/payroll/Periodicidad.java`
- `backend/src/main/java/com/staffly/backend/payslip/builder/PayslipBuilder.java`
- `frontend/src/app/features/payroll/models/payroll-config.ts`
- `frontend/src/app/features/payroll/components/payroll-config/payroll-config.component.ts`

Problema:

El frontend permite seleccionar `SEMANAL`, pero el backend no tiene esa periodicidad. Guardar esa opcion falla.

Comportamiento esperado:

O se implementa `SEMANAL` completamente o se elimina de la UI hasta que exista soporte.

Implementacion recomendada:

- Preferencia: implementar `SEMANAL` con horas nominales semanales configuradas o constante v1 documentada.
- Ajustar migraciones/datos si aplica.
- Agregar tests de config y calculo.

Validacion:

- Configurar periodicidad semanal desde UI.
- Backend persiste y calcula recibo sin error.

#### Issue 8: Optimizar listado de turnos con filtros en base de datos

Severidad: Medio

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/schedule/ScheduleService.java`
- `backend/src/main/java/com/staffly/backend/schedule/ScheduleRepository.java`

Problema:

`ScheduleService.list` carga todos los turnos de la empresa y filtra en memoria.

Comportamiento esperado:

Los filtros de empleado, sucursal, fechas y scope de supervisor deben resolverse en query.

Implementacion recomendada:

- Crear query/specification con filtros opcionales.
- Aplicar `branchIds` del supervisor en SQL.
- Mantener orden por `fechaHoraInicio`.
- Evaluar paginacion si la UI lo permite.

Validacion:

- Tests de filtros combinados.
- Test de supervisor sin sucursales retorna lista vacia.
- Prueba con dataset grande no carga toda la tabla.

#### Issue 9: Definir sueldo mensual vs calculo por horas cumplidas

Severidad: Medio

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/payslip/builder/PayslipBuilder.java`
- `docs/requerimientos-sistema-gestion-personal.md`

Problema:

El calculo toma `sueldoBase`, lo convierte a valor hora y paga segun turnos `CUMPLIDO`. Para empleados mensuales, si faltan turnos cumplidos, el bruto puede quedar en cero.

Comportamiento esperado:

Debe quedar definido si `sueldoBase` representa salario mensual fijo, base nominal para valor hora, o ambos segun `tipoContrato`.

Implementacion recomendada:

- Documentar regla v1 por `TipoContrato`.
- Si `JORNADA_COMPLETA` es mensual fija, partir de sueldo base y ajustar ausencias/licencias.
- Si es jornalizado/part-time, mantener calculo por horas cumplidas.
- Agregar tests por tipo de contrato.

Validacion:

- Empleado mensual sin turnos no cobra cero salvo regla explicita.
- Ausencias/licencias impactan como corresponda.
- Jornalizado sigue calculando por horas.

#### Issue 10: Alinear horas extra de reportes con liquidacion

Severidad: Medio

Ubicaciones iniciales:

- `backend/src/main/java/com/staffly/backend/report/HoursWorkedReportService.java`
- `backend/src/main/java/com/staffly/backend/payslip/builder/PayslipBuilder.java`

Problema:

El reporte agrupa horas extra por empleado+sucursal, mientras que la liquidacion agrupa por empleado y dia/semana. En empleados multi-sucursal pueden aparecer diferencias.

Comportamiento esperado:

El reporte debe aclarar o igualar la semantica de liquidacion.

Implementacion recomendada:

- Elegir una fuente de verdad para horas extra.
- Si el reporte es operacional por sucursal, mostrar horas trabajadas por sucursal y horas extra calculadas a nivel empleado con nota/campo separado.
- Agregar tests multi-sucursal.

Validacion:

- Empleado con turnos en dos sucursales el mismo dia muestra totales coherentes con payslip.

### P3 - Limpieza tecnica

#### Issue 11: Corregir warnings Angular localize/test setup

Severidad: Bajo

Ubicaciones iniciales:

- `frontend/src/main.ts`
- `frontend/src/test-setup.ts`
- `frontend/tsconfig*.json`
- `frontend/angular.json`

Problema:

Build/test muestran warnings por import directo de `@angular/localize/init` y `test-setup.ts` fuera del programa TypeScript.

Comportamiento esperado:

Build y test deben correr sin warnings evitables.

Implementacion recomendada:

- Mover `@angular/localize/init` a polyfills segun recomendacion Angular.
- Incluir `src/test-setup.ts` en el tsconfig correspondiente.
- Confirmar build/test limpios.

Validacion:

- `npm run build` sin warnings.
- `npm test -- --watch=false` sin warnings.

## Orden de implementacion recomendado

1. Issue 1, 2 y 3 en conjunto chico de PRs P0. No mezclar con frontend estetico.
2. Issue 4 y 5 despues de estabilizar estados de payslip.
3. Issue 6 antes de considerar turnos listos para roles reales.
4. Issue 7 a 10 como hardening funcional.
5. Issue 11 como limpieza final.

## Checklist de cierre del plan

- Cada issue tiene test de regresion.
- La documentacion de negocio queda actualizada si cambia una regla.
- Frontend y backend coinciden en DTOs.
- No queda ningun test esperando comportamiento contrario a los requisitos.
- `npm run build`, `npm test -- --watch=false` y backend test suite critica pasan.
- Se registra en cada PR que parte de este plan cierra.
