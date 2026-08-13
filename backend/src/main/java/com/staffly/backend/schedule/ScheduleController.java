// backend/src/main/java/com/staffly/backend/schedule/ScheduleController.java
package com.staffly.backend.schedule;

import com.staffly.backend.schedule.dto.CreateScheduleRequest;
import com.staffly.backend.schedule.dto.ScheduleResponse;
import com.staffly.backend.schedule.dto.UpdateScheduleRequest;
import com.staffly.backend.schedule.dto.UpdateStatusRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR','EMPLOYEE')")
    public List<ScheduleResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.list(employeeId, branchId, desde, hasta, principal);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(
            @Valid @RequestBody CreateScheduleRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.create(request, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR','EMPLOYEE')")
    public ScheduleResponse findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.findById(id, principal);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    public ScheduleResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduleRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        scheduleService.delete(id, principal);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    public ScheduleResponse confirm(
            @PathVariable UUID id,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.confirm(id, principal);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RRHH','SUPERVISOR')")
    public ScheduleResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal StafflyUserPrincipal principal) {
        return scheduleService.updateStatus(id, request, principal);
    }
}
