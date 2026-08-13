// backend/src/main/java/com/staffly/backend/holiday/HolidayController.java
package com.staffly.backend.holiday;

import com.staffly.backend.holiday.dto.CreateHolidayRequest;
import com.staffly.backend.holiday.dto.HolidayResponse;
import com.staffly.backend.holiday.dto.UpdateHolidayRequest;
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
@RequestMapping("/api/v1/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RRHH', 'SUPERVISOR')")
    public ResponseEntity<List<HolidayResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer anio,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(holidayService.list(branchId, anio, principal));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HolidayResponse> create(
            @Valid @RequestBody CreateHolidayRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        HolidayResponse response = holidayService.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/holidays/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HolidayResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHolidayRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return ResponseEntity.ok(holidayService.update(id, request, principal));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        holidayService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
