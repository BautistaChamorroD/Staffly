package com.staffly.backend.leave.dto;

import com.staffly.backend.leave.EstadoLicencia;
import com.staffly.backend.leave.LeaveRequest;

import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestResponse(
        UUID id,
        UUID employeeId,
        String employeeNombre,
        String employeeApellido,
        UUID leaveTypeId,
        String leaveTypeNombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String motivo,
        EstadoLicencia estado,
        String motivoRechazo,
        UUID aprobadoPorId,
        boolean tieneConflicto) {

    public static LeaveRequestResponse from(LeaveRequest lr) {
        return from(lr, false);
    }

    /**
     * @param tieneConflicto true si, estando PENDIENTE, ya se superpone con un turno
     *                       existente del empleado (misma semántica que approve()) —
     *                       advertencia proactiva en la card antes de intentar aprobar.
     */
    public static LeaveRequestResponse from(LeaveRequest lr, boolean tieneConflicto) {
        return new LeaveRequestResponse(
                lr.getId(),
                lr.getEmployeeId(),
                lr.getEmployee().getNombre(),
                lr.getEmployee().getApellido(),
                lr.getLeaveType().getId(),
                lr.getLeaveType().getNombre(),
                lr.getFechaInicio(),
                lr.getFechaFin(),
                lr.getMotivo(),
                lr.getEstado(),
                lr.getMotivoRechazo(),
                lr.getAprobadoPor(),
                tieneConflicto);
    }
}
