package com.staffly.backend.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record DuplicateWeeklyRequest(
        @Min(value = 1, message = "El mes debe estar entre 1 y 12")
        @Max(value = 12, message = "El mes debe estar entre 1 y 12")
        int mesObjetivo,

        @Positive(message = "El año debe ser positivo")
        int anioObjetivo
) {}
