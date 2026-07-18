package com.staffly.backend.employee.dto;

import com.staffly.backend.employee.TipoContrato;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateEmployeeRequest(
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank String documento,
        @NotNull LocalDate fechaNacimiento,
        @NotNull LocalDate fechaIngreso,
        LocalDate fechaEgreso,
        @NotNull TipoContrato tipoContrato,
        @NotBlank String categoria,
        @NotNull @PositiveOrZero BigDecimal sueldoBase,
        String telefono,
        String emailContacto,
        @NotEmpty List<UUID> branchIds) {
}
