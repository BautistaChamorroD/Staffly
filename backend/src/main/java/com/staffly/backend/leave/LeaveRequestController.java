package com.staffly.backend.leave;

import com.staffly.backend.leave.dto.CreateLeaveRequestRequest;
import com.staffly.backend.leave.dto.LeaveRequestResponse;
import com.staffly.backend.leave.dto.RejectLeaveRequestRequest;
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
@RequestMapping("/api/v1/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE')")
    public ResponseEntity<List<LeaveRequestResponse>> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) EstadoLicencia estado,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(leaveRequestService.list(employeeId, estado, principal));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<LeaveRequestResponse> create(
            @Valid @RequestBody CreateLeaveRequestRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        LeaveRequestResponse response = leaveRequestService.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/leave-requests/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE')")
    public ResponseEntity<LeaveRequestResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(leaveRequestService.getById(id, principal));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR')")
    public ResponseEntity<LeaveRequestResponse> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(leaveRequestService.approve(id, principal));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR')")
    public ResponseEntity<LeaveRequestResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectLeaveRequestRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(leaveRequestService.reject(id, request, principal));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<LeaveRequestResponse> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(leaveRequestService.cancel(id, principal));
    }
}
