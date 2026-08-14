CREATE TABLE payroll_config (
    id                      UUID            NOT NULL,
    company_id              UUID            NOT NULL,
    umbral_horas_extra      NUMERIC(10, 2)  NOT NULL DEFAULT 8,
    multiplicador_hora_extra NUMERIC(10, 4) NOT NULL DEFAULT 1.5,
    multiplicador_feriado   NUMERIC(10, 4)  NOT NULL DEFAULT 2.0,
    periodicidad            VARCHAR(20)     NOT NULL DEFAULT 'MENSUAL',
    conceptos_descuento     TEXT            NOT NULL DEFAULT '[]',
    CONSTRAINT pk_payroll_config         PRIMARY KEY (id),
    CONSTRAINT fk_payroll_config_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT uq_payroll_config_company UNIQUE (company_id)
);
