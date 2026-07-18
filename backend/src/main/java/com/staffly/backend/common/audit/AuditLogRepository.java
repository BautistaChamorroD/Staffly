package com.staffly.backend.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByCompanyIdAndEntityTypeAndEntityIdOrderByFechaDesc(
            UUID companyId, String entityType, UUID entityId);
}
