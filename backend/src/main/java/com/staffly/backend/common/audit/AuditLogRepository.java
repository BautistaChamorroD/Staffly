package com.staffly.backend.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByCompanyIdAndEntityTypeAndEntityIdOrderByFechaDesc(
            UUID companyId, String entityType, UUID entityId);

    /** Consulta transversal de auditoría (RF-28 / AUD-34 / issue #155), filtros todos opcionales. */
    @Query("SELECT a FROM AuditLog a WHERE a.companyId = :companyId " +
           "AND (:entityType IS NULL OR a.entityType = :entityType) " +
           "AND (:entityId IS NULL OR a.entityId = :entityId) " +
           "AND (:usuarioId IS NULL OR a.usuarioId = :usuarioId) " +
           "AND (:desde IS NULL OR a.fecha >= :desde) " +
           "AND (:hasta IS NULL OR a.fecha <= :hasta) " +
           "ORDER BY a.fecha DESC")
    List<AuditLog> findByFilters(@Param("companyId") UUID companyId,
                                 @Param("entityType") String entityType,
                                 @Param("entityId") UUID entityId,
                                 @Param("usuarioId") UUID usuarioId,
                                 @Param("desde") Instant desde,
                                 @Param("hasta") Instant hasta);
}
