CREATE TABLE employee_availability (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    employee_id UUID NOT NULL REFERENCES employee (id),
    dia_semana VARCHAR(10) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL
);

CREATE INDEX idx_employee_availability_employee ON employee_availability (employee_id);
