package com.staffly.backend.leave;

import com.staffly.backend.leave.dto.CreateLeaveTypeRequest;
import com.staffly.backend.leave.dto.LeaveTypeResponse;
import com.staffly.backend.leave.dto.UpdateLeaveTypeRequest;
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
@RequestMapping("/api/v1/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE')")
    public ResponseEntity<List<LeaveTypeResponse>> list(
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(leaveTypeService.list(principal));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LeaveTypeResponse> create(
            @Valid @RequestBody CreateLeaveTypeRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        LeaveTypeResponse response = leaveTypeService.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/leave-types/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LeaveTypeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeaveTypeRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(leaveTypeService.update(id, request, principal));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        leaveTypeService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
