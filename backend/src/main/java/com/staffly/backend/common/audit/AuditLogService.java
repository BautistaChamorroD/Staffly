package com.staffly.backend.common.audit;

import com.staffly.backend.common.audit.dto.AuditLogEntry;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> list(UUID companyId, String entityType, UUID entityId, UUID usuarioId,
                                    LocalDate desde, LocalDate hasta) {
        return auditLogRepository.findByFilters(
                        companyId, entityType, entityId, usuarioId,
                        desde != null ? desde.atStartOfDay(ZoneOffset.UTC).toInstant() : null,
                        hasta != null ? hasta.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant() : null)
                .stream()
                .map(AuditLogEntry::from)
                .toList();
    }
}
