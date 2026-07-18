CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES company (id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    campo VARCHAR(100) NOT NULL,
    valor_anterior VARCHAR(255),
    valor_nuevo VARCHAR(255),
    fecha TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
