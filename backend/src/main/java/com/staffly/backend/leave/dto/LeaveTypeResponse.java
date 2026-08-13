package com.staffly.backend.leave.dto;

import com.staffly.backend.leave.LeaveType;

import java.util.UUID;

public record LeaveTypeResponse(UUID id, String nombre, boolean esPaga, Integer cupoAnual) {

    public static LeaveTypeResponse from(LeaveType leaveType) {
        return new LeaveTypeResponse(
                leaveType.getId(),
                leaveType.getNombre(),
                leaveType.isEsPaga(),
                leaveType.getCupoAnual());
    }
}
