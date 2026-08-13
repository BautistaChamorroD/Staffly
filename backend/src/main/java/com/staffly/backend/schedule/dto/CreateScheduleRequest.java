package com.staffly.backend.schedule.dto;

import com.staffly.backend.schedule.TipoTurno;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateScheduleRequest(
        @NotNull UUID employeeId,
        @NotNull UUID branchId,
        @NotNull LocalDateTime fechaHoraInicio,
        @NotNull LocalDateTime fechaHoraFin,
        @NotNull TipoTurno tipoTurno
) {}
