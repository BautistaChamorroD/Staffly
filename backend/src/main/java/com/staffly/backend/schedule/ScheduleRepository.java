package com.staffly.backend.schedule;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    Optional<Schedule> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Schedule> findByCompanyId(UUID companyId);

    /**
     * Detecta solapamiento: cualquier turno existente del empleado cuyo intervalo
     * se interseque con [inicio, fin). excludeId se usa en PATCH para excluir el
     * turno que se está editando; pasar null en POST.
     */
    @Query("""
        SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
        FROM Schedule s
        WHERE s.companyId = :companyId
          AND s.employee.id = :employeeId
          AND s.fechaHoraInicio < :fin
          AND s.fechaHoraFin > :inicio
          AND (:excludeId IS NULL OR s.id <> :excludeId)
    """)
    boolean existsOverlap(
            @Param("companyId") UUID companyId,
            @Param("employeeId") UUID employeeId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("excludeId") UUID excludeId);

    /**
     * Retorna el ID del primer turno que solapa [inicio, fin) para el empleado.
     * Usar con PageRequest.of(0, 1) para obtener solo uno.
     */
    @Query("""
        SELECT s.id FROM Schedule s
        WHERE s.companyId = :companyId
          AND s.employee.id = :employeeId
          AND s.fechaHoraInicio < :fin
          AND s.fechaHoraFin > :inicio
        ORDER BY s.fechaHoraInicio ASC
    """)
    List<UUID> findConflictingIds(
            @Param("companyId") UUID companyId,
            @Param("employeeId") UUID employeeId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            Pageable pageable);
}
