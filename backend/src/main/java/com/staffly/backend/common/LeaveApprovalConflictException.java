package com.staffly.backend.common;

import com.staffly.backend.schedule.dto.ScheduleResponse;

import java.util.List;

public class LeaveApprovalConflictException extends RuntimeException {

    private final List<ScheduleResponse> turnosConflictivos;

    public LeaveApprovalConflictException(List<ScheduleResponse> turnosConflictivos) {
        super("La licencia se superpone con turnos existentes del empleado");
        this.turnosConflictivos = List.copyOf(turnosConflictivos);
    }

    public List<ScheduleResponse> getTurnosConflictivos() {
        return turnosConflictivos;
    }
}
