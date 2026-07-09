package com.staffly.backend.employee.dto;

import com.staffly.backend.employee.EstadoLaboral;
import jakarta.validation.constraints.NotNull;

public record UpdateEmployeeStatusRequest(@NotNull EstadoLaboral estadoLaboral) {
}
