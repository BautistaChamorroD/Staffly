package com.staffly.backend.schedule.dto;

import com.staffly.backend.schedule.TipoTurno;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateScheduleRequest(
        UUID branchId,
        LocalDateTime fechaHoraInicio,
        LocalDateTime fechaHoraFin,
        TipoTurno tipoTurno
) {}
