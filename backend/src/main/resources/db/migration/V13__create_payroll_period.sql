CREATE TABLE payroll_period (
    id            UUID        NOT NULL,
    company_id    UUID        NOT NULL,
    fecha_inicio  DATE        NOT NULL,
    fecha_fin     DATE        NOT NULL,
    estado        VARCHAR(20) NOT NULL DEFAULT 'ABIERTO',
    fecha_cierre  DATE,
    CONSTRAINT pk_payroll_period         PRIMARY KEY (id),
    CONSTRAINT fk_payroll_period_company FOREIGN KEY (company_id) REFERENCES company (id)
);
CREATE INDEX idx_payroll_period_company_estado ON payroll_period (company_id, estado);
