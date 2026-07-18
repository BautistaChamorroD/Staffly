-- Unicidad de documento por empresa (no global: dos empresas distintas
-- pueden tener cargada a la misma persona).
ALTER TABLE employee ADD CONSTRAINT uq_employee_company_documento UNIQUE (company_id, documento);
