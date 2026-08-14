package com.staffly.backend.advance.dto;

import com.staffly.backend.advance.Advance;
import com.staffly.backend.advance.EstadoAdelanto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AdvanceResponse(
        UUID id,
        UUID employeeId,
        String employeeNombre,
        String employeeApellido,
        LocalDate fecha,
        BigDecimal monto,
        String motivo,
        EstadoAdelanto estado
) {
    public static AdvanceResponse from(Advance a) {
        return new AdvanceResponse(
                a.getId(),
                a.getEmployeeId(),
                a.getEmployee().getNombre(),
                a.getEmployee().getApellido(),
                a.getFecha(),
                a.getMonto(),
                a.getMotivo(),
                a.getEstado()
        );
    }
}
