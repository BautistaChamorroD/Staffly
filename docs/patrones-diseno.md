# Staffly — Patrones de diseño a aplicar

Complementa el resto de la documentación. Estos patrones ya están decididos para evitar refactors futuros — se implementan directamente así al codear, no se reabren.

| Módulo / caso | Patrón | Dónde aplica |
|---|---|---|
| Creación de `Payslip` (normal vs. ajuste) | **Factory Method** | `payslip/` |
| Armado del cálculo de un `Payslip` (horas → licencias → descuentos → neto) | **Builder** | `payslip/` |
| Generación de PDF, futuras integraciones externas (gobierno, etc.) | **Adapter** | `common/` o el módulo que integre |
| Aplicación de conceptos de descuento de `PayrollConfig` sobre el bruto | **Decorator** | `payroll/` |
| Cálculo de hora extra / feriados (umbral configurable, extensible a futuro) | **Strategy** | `payroll/` |
| Transiciones de estado de `Payslip`, `LeaveRequest`, `Employee.estado_laboral` | **State** | `payslip/`, `leave/`, `employee/` |
| Proceso de cierre de período de nómina | **Template Method** | `payroll/` |
| Registro de `AuditLog` a partir de cambios en entidades | **Observer** (eventos de dominio de Spring) | `common/audit/` |
