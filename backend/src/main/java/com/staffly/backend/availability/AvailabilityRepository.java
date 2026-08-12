package com.staffly.backend.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityRepository extends JpaRepository<EmployeeAvailability, UUID> {

    /**
     * Lookup explícito por id + company_id: el tenantFilter de Hibernate no
     * cubre findById (ver TenantAwareEntity).
     */
    Optional<EmployeeAvailability> findByIdAndCompanyId(UUID id, UUID companyId);

    List<EmployeeAvailability> findByCompanyIdAndEmployeeId(UUID companyId, UUID employeeId);

    List<EmployeeAvailability> findByCompanyIdAndEmployeeIdAndDiaSemana(
            UUID companyId, UUID employeeId, DiaSemana diaSemana);
}
