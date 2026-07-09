package com.staffly.backend.branch.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalTime;

public record CreateBranchRequest(
        @NotBlank String nombre,
        @NotBlank String direccion,
        @NotBlank String zonaHoraria,
        LocalTime horarioVisibleInicio,
        LocalTime horarioVisibleFin) {
}
