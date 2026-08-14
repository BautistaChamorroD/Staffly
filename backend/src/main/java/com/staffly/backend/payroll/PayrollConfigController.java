package com.staffly.backend.payroll;

import com.staffly.backend.payroll.dto.PayrollConfigResponse;
import com.staffly.backend.payroll.dto.UpdatePayrollConfigRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payroll-config")
public class PayrollConfigController {

    private final PayrollConfigService service;

    public PayrollConfigController(PayrollConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayrollConfigResponse> get(@AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(service.get(principal));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayrollConfigResponse> update(
            @Valid @RequestBody UpdatePayrollConfigRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(service.update(request, principal));
    }
}
