package com.staffly.backend.payroll.dto;

import com.staffly.backend.payroll.ConceptoDescuento;
import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.Periodicidad;
import com.staffly.backend.payroll.TipoUmbral;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PayrollConfigResponse(
        UUID id,
        BigDecimal umbralHorasExtra,
        TipoUmbral tipoUmbral,
        BigDecimal multiplicadorHoraExtra,
        BigDecimal multiplicadorFeriado,
        Periodicidad periodicidad,
        List<ConceptoDescuentoDto> conceptosDescuento
) {

    public record ConceptoDescuentoDto(String nombre, String tipo, BigDecimal valor) {}

    public static PayrollConfigResponse from(PayrollConfig config) {
        return new PayrollConfigResponse(
                config.getId(),
                config.getUmbralHorasExtra(),
                config.getTipoUmbral(),
                config.getMultiplicadorHoraExtra(),
                config.getMultiplicadorFeriado(),
                config.getPeriodicidad(),
                config.getConceptosDescuento().stream()
                        .map(c -> new ConceptoDescuentoDto(c.getNombre(), c.getTipo().name(), c.getValor()))
                        .toList()
        );
    }
}
