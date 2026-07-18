package com.staffly.backend.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndDocumento(UUID companyId, String documento);

    boolean existsByCompanyIdAndDocumentoAndIdNot(UUID companyId, String documento, UUID id);
}
