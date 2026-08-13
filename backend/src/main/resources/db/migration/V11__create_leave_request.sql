CREATE TABLE leave_request (
    id              UUID          NOT NULL,
    company_id      UUID          NOT NULL,
    employee_id     UUID          NOT NULL,
    leave_type_id   UUID          NOT NULL,
    fecha_inicio    DATE          NOT NULL,
    fecha_fin       DATE          NOT NULL,
    motivo          VARCHAR(1000),
    estado          VARCHAR(50)   NOT NULL DEFAULT 'PENDIENTE',
    motivo_rechazo  VARCHAR(1000),
    CONSTRAINT pk_leave_request          PRIMARY KEY (id),
    CONSTRAINT fk_leave_request_company  FOREIGN KEY (company_id)    REFERENCES company (id),
    CONSTRAINT fk_leave_request_employee FOREIGN KEY (employee_id)   REFERENCES employee (id),
    CONSTRAINT fk_leave_request_type     FOREIGN KEY (leave_type_id) REFERENCES leave_type (id)
);
CREATE INDEX idx_leave_request_company_employee ON leave_request (company_id, employee_id);
CREATE INDEX idx_leave_request_company_estado   ON leave_request (company_id, estado);
