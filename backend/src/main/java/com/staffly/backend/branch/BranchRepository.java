package com.staffly.backend.branch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    /**
     * Lookup explícito por id + company_id. Hibernate NO aplica el filtro
     * "tenantFilter" a EntityManager.find()/Session.get() (solo a queries
     * HQL/Criteria) — usar plain findById() acá filtraría datos de otra
     * empresa. Ver TenantAwareEntity.
     */
    Optional<Branch> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Branch> findByCompanyId(UUID companyId);

    /** Alcance de SUPERVISOR: solo sus sucursales asignadas, resuelto en SQL. */
    List<Branch> findByCompanyIdAndIdIn(UUID companyId, Collection<UUID> ids);
}
