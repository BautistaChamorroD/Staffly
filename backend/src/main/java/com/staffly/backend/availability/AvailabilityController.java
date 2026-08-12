package com.staffly.backend.availability;

import com.staffly.backend.availability.dto.AvailabilityResponse;
import com.staffly.backend.availability.dto.CreateAvailabilityRequest;
import com.staffly.backend.availability.dto.UpdateAvailabilityRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees/{employeeId}/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE')")
    public ResponseEntity<List<AvailabilityResponse>> list(
            @PathVariable UUID employeeId, @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(availabilityService.list(employeeId, principal));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<AvailabilityResponse> create(
            @PathVariable UUID employeeId,
            @Valid @RequestBody CreateAvailabilityRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        AvailabilityResponse response = availabilityService.create(employeeId, request, principal);
        return ResponseEntity
                .created(URI.create("/api/v1/employees/" + employeeId + "/availability/" + response.id()))
                .body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<AvailabilityResponse> update(
            @PathVariable UUID employeeId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAvailabilityRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(availabilityService.update(employeeId, id, request, principal));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID employeeId,
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        availabilityService.delete(employeeId, id, principal);
        return ResponseEntity.noContent().build();
    }
}
