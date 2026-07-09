package com.staffly.backend.branch.dto;

import com.staffly.backend.branch.EstadoSucursal;
import jakarta.validation.constraints.NotNull;

public record UpdateBranchStatusRequest(@NotNull EstadoSucursal estado) {
}
