package com.staffly.backend.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndDocumento(UUID companyId, String documento);

    boolean existsByCompanyIdAndDocumentoAndIdNot(UUID companyId, String documento, UUID id);

    @Query("""
        SELECT e FROM Employee e WHERE e.companyId = :companyId
        AND (e.estadoLaboral <> :baja OR e.estadoLiquidacion = :pendiente)
    """)
    List<Employee> findByCompanyIdForPayroll(
            @Param("companyId") UUID companyId,
            @Param("baja") EstadoLaboral baja,
            @Param("pendiente") EstadoLiquidacion pendiente);
}
