package com.staffly.backend.availability.dto;

import com.staffly.backend.availability.DiaSemana;

import java.time.LocalTime;

/**
 * Actualización parcial: campos nulos se dejan sin tocar (mismo patrón que
 * el resto de los PATCH del sistema).
 */
public record UpdateAvailabilityRequest(DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin) {
}
