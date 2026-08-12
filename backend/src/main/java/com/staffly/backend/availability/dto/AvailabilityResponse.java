package com.staffly.backend.availability.dto;

import com.staffly.backend.availability.DiaSemana;
import com.staffly.backend.availability.EmployeeAvailability;

import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityResponse(UUID id, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin) {

    public static AvailabilityResponse from(EmployeeAvailability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getDiaSemana(),
                availability.getHoraInicio(),
                availability.getHoraFin());
    }
}
