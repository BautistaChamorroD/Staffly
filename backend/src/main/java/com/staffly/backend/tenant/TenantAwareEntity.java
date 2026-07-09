package com.staffly.backend.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Superclase de toda entidad de negocio que pertenece a una empresa (tenant).
 * Define el filtro de Hibernate "tenantFilter" pero no lo activa: cada entidad
 * concreta debe declarar @Filter(name = "tenantFilter", condition = "company_id = :companyId"),
 * y la activación en runtime (a partir del JWT) se hace en TenantFilterInterceptor.
 *
 * <p><b>Importante — el filtro NO cubre EntityManager.find()/repository.findById().</b>
 * Hibernate solo aplica @Filter a queries HQL/Criteria (ej. findAll(), derived
 * queries), nunca a un lookup directo por clave primaria. Cualquier repositorio
 * de una entidad que extienda esta clase debe exponer un método explícito tipo
 * {@code findByIdAndCompanyId(id, companyId)} para lookups por id — usar
 * {@code findById(id)} a secas filtra datos de otra empresa. Descubierto y
 * corregido en BE-1.4 (ver BranchRepository).</p>
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "companyId", type = UUID.class))
public abstract class TenantAwareEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }
}
