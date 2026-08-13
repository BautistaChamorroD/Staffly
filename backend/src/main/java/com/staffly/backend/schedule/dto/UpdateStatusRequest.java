package com.staffly.backend.schedule.dto;

import com.staffly.backend.schedule.EstadoTurno;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull EstadoTurno estado) {}
