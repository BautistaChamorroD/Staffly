package com.staffly.backend.availability.dto;

import com.staffly.backend.availability.DiaSemana;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CreateAvailabilityRequest(
        @NotNull DiaSemana diaSemana,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin) {
}
