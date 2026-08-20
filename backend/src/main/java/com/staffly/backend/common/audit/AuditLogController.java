package com.staffly.backend.common.audit;

import com.staffly.backend.common.audit.dto.AuditLogEntry;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** RF-28 / AUD-34 (issue #155): consulta transversal de auditoría, ver `docs/api-design.md` sección 15. */
@RestController
@RequestMapping("/api/v1/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLogEntry>> list(
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) UUID entidadId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(
                auditLogService.list(principal.getCompanyId(), entidad, entidadId, userId, desde, hasta));
    }
}
