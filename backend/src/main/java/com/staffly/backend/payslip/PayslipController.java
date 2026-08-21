package com.staffly.backend.payslip;

import com.staffly.backend.payslip.dto.MarkPaidRequest;
import com.staffly.backend.payslip.dto.PayslipResponse;
import com.staffly.backend.payslip.dto.VoidPayslipRequest;
import com.staffly.backend.payslip.pdf.PayslipPdfService;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payslips")
public class PayslipController {

    private final PayslipService    payslipService;
    private final PayslipPdfService pdfService;

    public PayslipController(PayslipService payslipService,
                             PayslipPdfService pdfService) {
        this.payslipService = payslipService;
        this.pdfService     = pdfService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<List<PayslipResponse>> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID payrollPeriodId,
            @RequestParam(required = false) EstadoRecibo estado,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(payslipService.list(employeeId, payrollPeriodId, estado, principal));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<List<PayslipResponse>> me(
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(payslipService.list(null, null, null, principal));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<PayslipResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(payslipService.getById(id, principal));
    }

    @PatchMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<PayslipResponse> markPaid(
            @PathVariable UUID id,
            @RequestBody(required = false) MarkPaidRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(payslipService.markPaid(id, request, principal));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayslipResponse> voidAndAdjust(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) VoidPayslipRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(payslipService.voidAndAdjust(id, request, principal));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<byte[]> getPdf(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        byte[] pdf = pdfService.generatePdf(id, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=recibo-" + id + ".pdf")
                .body(pdf);
    }
}
