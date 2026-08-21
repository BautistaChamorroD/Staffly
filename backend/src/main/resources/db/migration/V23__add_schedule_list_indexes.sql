CREATE INDEX idx_schedule_company_fecha_inicio
    ON schedule (company_id, fecha_hora_inicio);

CREATE INDEX idx_schedule_company_branch_fecha_inicio
    ON schedule (company_id, branch_id, fecha_hora_inicio);

CREATE INDEX idx_schedule_company_employee_fecha_inicio
    ON schedule (company_id, employee_id, fecha_hora_inicio);
