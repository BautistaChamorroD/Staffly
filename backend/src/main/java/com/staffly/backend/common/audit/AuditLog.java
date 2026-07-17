package com.staffly.backend.common.audit;

import com.staffly.backend.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro genérico de un cambio auditable sobre una entidad de negocio
 * (BE-1.8, registro mínimo). Un row por campo modificado: qué entidad, qué
 * campo, valor anterior/nuevo, quién y cuándo. La consulta transversal con
 * filtros llega en BE-5.1; por ahora solo lo consume el historial de
 * Employee (RF-06).
 */
@Entity
@Table(name = "audit_log")
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class AuditLog extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "campo", nullable = false, updatable = false)
    private String campo;

    @Column(name = "valor_anterior", updatable = false)
    private String valorAnterior;

    @Column(name = "valor_nuevo", updatable = false)
    private String valorNuevo;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha = Instant.now();

    public UUID getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public void setValorNuevo(String valorNuevo) {
        this.valorNuevo = valorNuevo;
    }

    public Instant getFecha() {
        return fecha;
    }
}
