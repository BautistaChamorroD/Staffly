# Reglas de liquidacion v1 por tipo de contrato

Fecha: 2026-08-21

## Objetivo

Definir como interpreta Staffly `sueldoBase` al calcular recibos de sueldo.

## Reglas

- `JORNADA_COMPLETA`: `sueldoBase` representa el salario fijo del periodo liquidado. Un empleado de jornada completa cobra ese importe aunque no tenga turnos `CUMPLIDO` cargados en el periodo.
- `JORNADA_PARCIAL`: `sueldoBase` se usa como base nominal para calcular valor hora segun la periodicidad de nomina. El bruto se calcula con las horas `CUMPLIDO`.
- `POR_HORA`: `sueldoBase` se usa como base nominal para calcular valor hora segun la periodicidad de nomina. El bruto se calcula con las horas `CUMPLIDO`.

## Licencias

- En `JORNADA_COMPLETA`, una licencia paga no suma un importe adicional porque ya esta cubierta por el salario fijo del periodo.
- En `JORNADA_COMPLETA`, una licencia no paga descuenta dias del salario fijo usando `valorHoraBase * 8`.
- En `JORNADA_PARCIAL` y `POR_HORA`, una licencia paga suma dias pagos usando `valorHoraBase * 8`, y una licencia no paga descuenta con la misma regla.

## Horas especiales

Las horas extra y feriados trabajados se calculan sobre los turnos `CUMPLIDO` del periodo. En `JORNADA_COMPLETA`, esas horas se agregan al salario fijo como conceptos variables.
