package com.staffly.backend.payroll;

import com.staffly.backend.payroll.dto.CreatePayrollPeriodRequest;
import com.staffly.backend.payroll.dto.PayrollPeriodResponse;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payroll-periods")
public class PayrollPeriodController {

    private final PayrollPeriodService      service;
    private final PayrollPeriodCloseService closeService;

    public PayrollPeriodController(PayrollPeriodService service,
                                   PayrollPeriodCloseService closeService) {
        this.service      = service;
        this.closeService = closeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<List<PayrollPeriodResponse>> list(
            @RequestParam(required = false) EstadoPeriodo estado,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(service.list(estado, principal));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<PayrollPeriodResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(service.getById(id, principal));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<PayrollPeriodResponse> create(
            @Valid @RequestBody CreatePayrollPeriodRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        PayrollPeriodResponse response = service.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/payroll-periods/" + response.id())).body(response);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayrollPeriodResponse> close(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(closeService.close(id, principal.getCompanyId()));
    }
}
