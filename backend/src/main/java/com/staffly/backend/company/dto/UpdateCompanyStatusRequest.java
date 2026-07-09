package com.staffly.backend.company.dto;

import com.staffly.backend.company.EstadoEmpresa;
import jakarta.validation.constraints.NotNull;

public record UpdateCompanyStatusRequest(@NotNull EstadoEmpresa estado) {
}
