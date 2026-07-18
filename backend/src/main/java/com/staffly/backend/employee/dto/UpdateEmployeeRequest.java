package com.staffly.backend.employee.dto;

import com.staffly.backend.employee.TipoContrato;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Actualización parcial: campos nulos se dejan sin tocar. estadoLaboral se
 * cambia únicamente vía PATCH /employees/{id}/status, no acá. estadoLiquidacion
 * no es editable por API (lo maneja la lógica de nómina, Fase 3).
 */
public record UpdateEmployeeRequest(
        String nombre,
        String apellido,
        String documento,
        LocalDate fechaNacimiento,
        LocalDate fechaIngreso,
        LocalDate fechaEgreso,
        TipoContrato tipoContrato,
        String categoria,
        @PositiveOrZero BigDecimal sueldoBase,
        String telefono,
        String emailContacto,
        List<UUID> branchIds) {
}
