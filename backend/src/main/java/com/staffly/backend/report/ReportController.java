package com.staffly.backend.report;

import com.staffly.backend.report.dto.HoursWorkedRow;
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

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final HoursWorkedReportService hoursWorkedReportService;

    public ReportController(HoursWorkedReportService hoursWorkedReportService) {
        this.hoursWorkedReportService = hoursWorkedReportService;
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
}
