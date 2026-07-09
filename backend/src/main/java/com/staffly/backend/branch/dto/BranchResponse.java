package com.staffly.backend.branch.dto;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.EstadoSucursal;

import java.time.LocalTime;
import java.util.UUID;

public record BranchResponse(
        UUID id,
        String nombre,
        String direccion,
        String zonaHoraria,
        EstadoSucursal estado,
        LocalTime horarioVisibleInicio,
        LocalTime horarioVisibleFin) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getNombre(),
                branch.getDireccion(),
                branch.getZonaHoraria(),
                branch.getEstado(),
                branch.getHorarioVisibleInicio(),
                branch.getHorarioVisibleFin());
    }
}
