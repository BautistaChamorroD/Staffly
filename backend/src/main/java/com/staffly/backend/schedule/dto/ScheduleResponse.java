package com.staffly.backend.schedule.dto;

import com.staffly.backend.schedule.EstadoTurno;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.TipoTurno;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        UUID employeeId,
        UUID branchId,
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        TipoTurno tipoTurno,
        EstadoTurno estado,
        double horasTotales,
        String warning
) {
    public static ScheduleResponse from(Schedule s) {
        return from(s, null);
    }

    public static ScheduleResponse from(Schedule s, String warning) {
        double horas = Duration.between(s.getFechaHoraInicio(), s.getFechaHoraFin()).toMinutes() / 60.0;
        return new ScheduleResponse(
                s.getId(),
                s.getEmployee().getId(),
                s.getBranch().getId(),
                s.getFechaHoraInicio(),
                s.getFechaHoraFin(),
                s.getTipoTurno(),
                s.getEstado(),
                horas,
                warning);
    }
}
