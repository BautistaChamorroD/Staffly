package com.staffly.backend.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    Optional<LeaveRequest> findByIdAndCompanyId(UUID id, UUID companyId);

    /**
     * Carga todas las solicitudes de la empresa junto con el employee y sus
     * sucursales en un solo query — necesario para el filtrado de SUPERVISOR
     * sin generar N+1.
     */
    @Query("""
        SELECT lr FROM LeaveRequest lr
        JOIN FETCH lr.employee e
        LEFT JOIN FETCH e.branches
        JOIN FETCH lr.leaveType
        WHERE lr.companyId = :companyId
    """)
    List<LeaveRequest> findByCompanyIdWithDetails(@Param("companyId") UUID companyId);

    @Query("""
        SELECT lr FROM LeaveRequest lr
        JOIN FETCH lr.employee e
        LEFT JOIN FETCH e.branches
        JOIN FETCH lr.leaveType
        WHERE lr.companyId = :companyId AND lr.employee.id = :employeeId
    """)
    List<LeaveRequest> findByCompanyIdAndEmployeeIdWithDetails(
            @Param("companyId") UUID companyId,
            @Param("employeeId") UUID employeeId);

    @Query("""
        SELECT lr FROM LeaveRequest lr
        JOIN FETCH lr.leaveType
        WHERE lr.companyId = :companyId
          AND lr.employee.id = :employeeId
          AND lr.estado = :estado
    """)
    List<LeaveRequest> findByCompanyIdAndEmployeeIdAndEstado(
            @Param("companyId") UUID companyId,
            @Param("employeeId") UUID employeeId,
            @Param("estado") EstadoLicencia estado);

    /**
     * IDs de licencias APROBADA del empleado cuyo rango de días [fechaInicio, fechaFin]
     * se superpone con [desde, hasta] (ambos inclusive, granularidad de día).
     */
    @Query("""
        SELECT lr.id FROM LeaveRequest lr
        WHERE lr.companyId = :companyId
          AND lr.employee.id = :employeeId
          AND lr.estado = com.staffly.backend.leave.EstadoLicencia.APROBADA
          AND lr.fechaInicio <= :hasta
          AND lr.fechaFin >= :desde
    """)
    List<UUID> findApprovedOverlappingIds(
            @Param("companyId") UUID companyId,
            @Param("employeeId") UUID employeeId,
            @Param("desde") java.time.LocalDate desde,
            @Param("hasta") java.time.LocalDate hasta);
}
