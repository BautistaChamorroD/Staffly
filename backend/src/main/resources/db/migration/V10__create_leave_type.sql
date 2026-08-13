CREATE TABLE leave_type (
    id          UUID         NOT NULL,
    company_id  UUID         NOT NULL,
    nombre      VARCHAR(255) NOT NULL,
    es_paga     BOOLEAN      NOT NULL DEFAULT TRUE,
    cupo_anual  INTEGER,
    CONSTRAINT pk_leave_type PRIMARY KEY (id),
    CONSTRAINT fk_leave_type_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT uq_leave_type_company_nombre UNIQUE (company_id, nombre)
);
