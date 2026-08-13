CREATE TABLE schedule (
    id UUID NOT NULL PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    employee_id UUID NOT NULL REFERENCES employee (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    fecha_hora_inicio TIMESTAMP NOT NULL,
    fecha_hora_fin TIMESTAMP NOT NULL,
    tipo_turno VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PLANIFICADO'
);

CREATE INDEX idx_schedule_company_employee ON schedule(company_id, employee_id);
