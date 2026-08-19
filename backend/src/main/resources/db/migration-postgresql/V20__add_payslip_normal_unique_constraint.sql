-- Defensa en profundidad contra Payslips NORMAL duplicados por empleado+período
-- (issue #147, parte a) — la app ya serializa el cierre con un lock pesimista y
-- hace upsert al volver a cerrar, pero este índice cubre cualquier vía que se
-- salte esa lógica (ej. un insert manual o un bug futuro). Parcial porque un
-- AJUSTE convive legítimamente con el original (ANULADO) del mismo
-- empleado+período — no puede ser un UNIQUE compuesto estándar sobre toda la tabla.
CREATE UNIQUE INDEX uq_payslip_normal_company_employee_period
    ON payslip (company_id, employee_id, payroll_period_id)
    WHERE tipo = 'NORMAL';
