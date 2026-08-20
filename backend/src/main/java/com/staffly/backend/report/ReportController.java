package com.staffly.backend.report;

import com.staffly.backend.report.dto.HoursWorkedRow;
import com.staffly.backend.report.dto.PayrollCostRow;
import com.staffly.backend.report.dto.PendingAdvanceRow;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final HoursWorkedReportService hoursWorkedReportService;
    private final PayrollCostReportService payrollCostReportService;
    private final PendingAdvancesReportService pendingAdvancesReportService;
    private final ReportExportService      reportExportService;

    public ReportController(HoursWorkedReportService hoursWorkedReportService,
                            PayrollCostReportService payrollCostReportService,
                            PendingAdvancesReportService pendingAdvancesReportService,
                            ReportExportService reportExportService) {
        this.hoursWorkedReportService = hoursWorkedReportService;
        this.payrollCostReportService = payrollCostReportService;
        this.pendingAdvancesReportService = pendingAdvancesReportService;
        this.reportExportService = reportExportService;
    }

    @GetMapping("/hours-worked")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<List<HoursWorkedRow>> hoursWorked(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(
                hoursWorkedReportService.generate(principal.getCompanyId(), branchId, desde, hasta));
    }

    @GetMapping("/payroll-cost")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<List<PayrollCostRow>> payrollCost(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(
                payrollCostReportService.generate(principal.getCompanyId(), branchId, desde, hasta));
    }

    @GetMapping("/pending-advances")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<List<PendingAdvanceRow>> pendingAdvances(
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(pendingAdvancesReportService.generate(principal.getCompanyId()));
    }

    @GetMapping("/{report}/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<byte[]> export(
            @PathVariable String report,
            @RequestParam String format,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        byte[] content = reportExportService.export(report, format, principal.getCompanyId(), branchId, desde, hasta);
        MediaType contentType = "pdf".equals(format) ? MediaType.APPLICATION_PDF : new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + report + "." + format)
                .body(content);
    }
}
