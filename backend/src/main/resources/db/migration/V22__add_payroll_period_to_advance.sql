ALTER TABLE advance
    ADD COLUMN payroll_period_id UUID;

ALTER TABLE advance
    ADD CONSTRAINT fk_advance_payroll_period
        FOREIGN KEY (payroll_period_id) REFERENCES payroll_period (id);

CREATE INDEX idx_advance_payroll_period
    ON advance (payroll_period_id);

CREATE INDEX idx_advance_company_employee_estado_fecha
    ON advance (company_id, employee_id, estado, fecha);
