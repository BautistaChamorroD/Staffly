package com.staffly.backend.advance;

import com.staffly.backend.advance.dto.AdvanceResponse;
import com.staffly.backend.advance.dto.CreateAdvanceRequest;
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
@RequestMapping("/api/v1/advances")
public class AdvanceController {

    private final AdvanceService advanceService;

    public AdvanceController(AdvanceService advanceService) {
        this.advanceService = advanceService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<List<AdvanceResponse>> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) EstadoAdelanto estado,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(advanceService.list(employeeId, estado, principal));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<AdvanceResponse> create(
            @Valid @RequestBody CreateAdvanceRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        AdvanceResponse response = advanceService.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/advances/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'EMPLOYEE')")
    public ResponseEntity<AdvanceResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(advanceService.getById(id, principal));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        advanceService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
