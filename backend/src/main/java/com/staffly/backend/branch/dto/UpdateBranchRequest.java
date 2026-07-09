package com.staffly.backend.branch.dto;

import java.time.LocalTime;

/**
 * Actualización parcial: los campos nulos se dejan sin tocar. El estado se
 * cambia únicamente vía PATCH /branches/{id}/status, no acá.
 */
public record UpdateBranchRequest(
        String nombre,
        String direccion,
        String zonaHoraria,
        LocalTime horarioVisibleInicio,
        LocalTime horarioVisibleFin) {
}
