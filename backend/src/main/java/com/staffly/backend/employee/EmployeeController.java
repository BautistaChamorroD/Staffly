package com.staffly.backend.employee;

import com.staffly.backend.employee.dto.CreateEmployeeRequest;
import com.staffly.backend.employee.dto.EmployeeHistoryEntry;
import com.staffly.backend.employee.dto.EmployeeResponse;
import com.staffly.backend.employee.dto.UpdateEmployeeRequest;
import com.staffly.backend.employee.dto.UpdateEmployeeStatusRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR')")
    public ResponseEntity<List<EmployeeResponse>> list(
            @RequestParam(required = false) EstadoLaboral estadoLaboral,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(employeeService.list(estadoLaboral, branchId, search, principal));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<EmployeeResponse> me(@AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(employeeService.getMe(principal));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR')")
    public ResponseEntity<EmployeeResponse> getById(
            @PathVariable UUID id, @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(employeeService.getById(id, principal));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<List<EmployeeHistoryEntry>> history(
            @PathVariable UUID id, @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(employeeService.getHistory(id, principal));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<EmployeeResponse> create(
            @Valid @RequestBody CreateEmployeeRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        EmployeeResponse response = employeeService.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/employees/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(employeeService.update(id, request, principal));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<EmployeeResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeStatusRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(employeeService.updateStatus(id, request, principal));
    }
}
