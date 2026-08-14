CREATE TABLE advance (
    id          UUID            NOT NULL,
    company_id  UUID            NOT NULL,
    employee_id UUID            NOT NULL,
    fecha       DATE            NOT NULL,
    monto       NUMERIC(14, 2)  NOT NULL,
    motivo      VARCHAR(500),
    estado      VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE',
    CONSTRAINT pk_advance             PRIMARY KEY (id),
    CONSTRAINT fk_advance_company     FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_advance_employee    FOREIGN KEY (employee_id) REFERENCES employee (id)
);
