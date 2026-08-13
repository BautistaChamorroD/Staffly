CREATE TABLE holiday (
    id          UUID        NOT NULL,
    company_id  UUID        NOT NULL,
    branch_id   UUID,
    fecha       DATE        NOT NULL,
    nombre      VARCHAR(255) NOT NULL,
    recurrente  BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_holiday PRIMARY KEY (id),
    CONSTRAINT fk_holiday_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_holiday_branch  FOREIGN KEY (branch_id)  REFERENCES branch (id)
);

CREATE INDEX idx_holiday_company_fecha ON holiday (company_id, fecha);
