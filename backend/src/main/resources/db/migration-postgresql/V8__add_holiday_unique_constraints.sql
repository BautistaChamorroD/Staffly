-- Restricciones únicas para evitar duplicados concurrentes en holiday.
-- Un índice parcial por caso: feriados globales (branch_id IS NULL) y por sucursal.
-- No se puede usar un UNIQUE compuesto estándar porque NULL no se compara como igual en SQL.

CREATE UNIQUE INDEX uq_holiday_company_global
    ON holiday(company_id, fecha)
    WHERE branch_id IS NULL;

CREATE UNIQUE INDEX uq_holiday_company_branch
    ON holiday(company_id, branch_id, fecha)
    WHERE branch_id IS NOT NULL;
