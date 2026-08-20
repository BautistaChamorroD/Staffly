package com.staffly.backend.advance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdvanceRepository extends JpaRepository<Advance, UUID> {

    @Query("SELECT a FROM Advance a JOIN FETCH a.employee WHERE a.companyId = :companyId ORDER BY a.fecha DESC")
    List<Advance> findByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT a FROM Advance a JOIN FETCH a.employee WHERE a.companyId = :companyId AND a.employee.id = :employeeId ORDER BY a.fecha DESC")
    List<Advance> findByCompanyIdAndEmployeeId(@Param("companyId") UUID companyId, @Param("employeeId") UUID employeeId);

    @Query("SELECT a FROM Advance a JOIN FETCH a.employee WHERE a.id = :id AND a.companyId = :companyId")
    Optional<Advance> findByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("SELECT a FROM Advance a JOIN FETCH a.employee WHERE a.companyId = :companyId AND a.employee.id = :employeeId AND a.estado = :estado ORDER BY a.fecha DESC")
    List<Advance> findByCompanyIdAndEmployeeIdAndEstado(@Param("companyId") UUID companyId, @Param("employeeId") UUID employeeId, @Param("estado") EstadoAdelanto estado);

    /** Adelantos pendientes para el reporte RF-24 (AUD-32 / issue #153). */
    @Query("SELECT a FROM Advance a JOIN FETCH a.employee WHERE a.companyId = :companyId AND a.estado = :estado ORDER BY a.fecha ASC")
    List<Advance> findByCompanyIdAndEstado(@Param("companyId") UUID companyId, @Param("estado") EstadoAdelanto estado);
}
