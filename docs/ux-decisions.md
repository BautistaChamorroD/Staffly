# Staffly — Decisiones de UX (wireframes v1)

Complementa `requerimientos-sistema-gestion-personal.md` y `api-design.md`. Documenta las decisiones de diseño de pantalla validadas antes de pasar a implementación, para que el frontend (y quien construya sobre `staffly-frontend`) no tenga que re-descubrirlas.

---

## 1. Pantalla de armado de horarios (Schedule builder)

**Decisión final: línea de tiempo continua, no franjas fijas ni fila-por-empleado.**

### Por qué

Se evaluaron 3 enfoques antes de llegar a este:

1. ❌ **Fila por empleado, columna por día** (primer intento): optimiza para "¿qué días trabaja María?", pero el problema real de quien arma los horarios es de **cobertura** ("¿quién cubre el jueves a la tarde?"), no de agenda individual.
2. ❌ **Franjas fijas (mañana/tarde/noche) con nombre dentro de la celda**: mejor que la opción 1 (muestra cobertura), pero fuerza a los turnos a "encajar" en bloques predefinidos, lo cual no representa bien turnos de duración variable ni turnos que cruzan medianoche.
3. ✅ **Línea de tiempo continua (elegida)**: columnas por día, eje vertical de horas continuo. Cada turno es un bloque posicionado y dimensionado según su hora de inicio/fin real. Los huecos sin cubrir se ven como espacio vacío real en la línea de tiempo, con un control `+` para asignar directo ahí.

### Elementos de la pantalla

- **Navegación semanal** (flechas + rango de fechas) y selector de sucursal.
- **Columnas por día** (lun a dom), fin de semana diferenciado visualmente.
- **Eje vertical de horas**, con rango visible configurable por sucursal (`Branch.horario_visible_inicio/fin`) — solo a fines de visualización, no restringe en qué horario se puede asignar un turno.
- **Bloques de turno** dentro de cada columna: muestran el nombre del empleado y el horario, posicionados según su hora real (no en franjas fijas).
- **Turnos que cruzan medianoche**: el bloque llega hasta el borde inferior del día en que empieza, y el día siguiente muestra un bloque de continuación con la referencia "continúa de [día] [hora]hs".
- **Huecos sin cubrir**: espacio vacío en la línea de tiempo con un control `+` centrado para asignar un turno directamente ahí.
- **Advertencia de disponibilidad** (RF-10): borde punteado ámbar + ícono de alerta en el bloque del turno, cuando se asigna fuera de la disponibilidad declarada del empleado. No bloquea, solo advierte.
- **Licencia aprobada** (RF-15e): se muestra como un bloque con estilo diferenciado (color de acento), ocupando el rango de fechas de la licencia, y no se puede asignar un turno superpuesto ahí (bloqueo duro).

### Pendiente de definir en diseño visual final (no bloqueante para avanzar)
- Comportamiento exacto de click en un hueco vacío (¿abre un modal, o un dropdown rápido de selección de empleado?).
- Cómo se resuelven visualmente 2+ turnos superpuestos de distintos empleados en la misma franja horaria (¿columnas lado a lado dentro del mismo día?) — no debería pasar por el bloqueo de solapamiento del mismo empleado, pero sí puede pasar con empleados distintos cubriendo el mismo horario.

---

## 2. Pantalla de recibo de sueldo (Payslip)

**Decisión final: card simple con desglose en 3 bloques (haberes / descuentos / neto), validada sin cambios en la primera propuesta.**

### Elementos de la pantalla

- Encabezado: nombre del empleado, período, badge de estado (`generado`/`pagado`/`anulado`/`ajuste`).
- Bloque **haberes**: horas normales, horas extra, feriados trabajados — cada uno con su monto.
- Bloque **descuentos**: conceptos configurables (impuestos, cargas sociales, etc.) + adelantos descontados en el período, cada uno con su monto en negativo.
- **Neto a pagar**, destacado visualmente al final.
- Acciones: descargar PDF (prioridad definida, ver decisión #4 en el documento de requerimientos), ver historial de liquidaciones del empleado.
- Para un `Payslip` con estado `ajuste`: reusar el mismo diseño, agregando una referencia visible al recibo original que ajusta (`payslip_original_id`).

---

## 3. Listado de empleados

Tabla con columnas: nombre, sucursal, puesto, **estado laboral** y **estado de liquidación** como columnas separadas (reflejan los dos campos independientes definidos en el modelo — ej. un empleado puede aparecer como `baja` + `pendiente` simultáneamente). Filtros por sucursal y estado, búsqueda por nombre/documento. Botón de alta rápida.

## 4. Aprobación de solicitudes de licencia

Lista de solicitudes con acciones directas de aprobar/rechazar en cada card. Caso especial a destacar: si la solicitud se superpone con un turno ya asignado (`Schedule` existente), se muestra la advertencia de conflicto directo en la card **antes** de que RRHH/Supervisor intente aprobar — anticipa visualmente el 409 que devolvería `POST /leave-requests/{id}/approve` en ese caso, en vez de que el usuario se entere recién al hacer click en "aprobar".

---

## Próximas pantallas a diseñar (pendientes, nivel de detalle menor — se resuelven sobre la marcha en implementación)

- Formulario de alta/edición de empleado (campos ya definidos en el modelo, no requiere mayor definición de UX).
- Dashboard/resumen para Admin (reportes de horas y costos).
- Configuración de `PayrollConfig` (reglas de nómina por empresa).
