package com.staffly.backend.payroll.dto;

import com.staffly.backend.payroll.Periodicidad;
import com.staffly.backend.payroll.TipoUmbral;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdatePayrollConfigRequest(
        @NotNull @DecimalMin("0.0") BigDecimal umbralHorasExtra,
        @NotNull TipoUmbral tipoUmbral,
        @NotNull @DecimalMin("1.0") BigDecimal multiplicadorHoraExtra,
        @NotNull @DecimalMin("1.0") BigDecimal multiplicadorFeriado,
        @NotNull Periodicidad periodicidad,
        @Valid @Size(max = 20) List<ConceptoDescuentoRequest> conceptosDescuento
) {

    public record ConceptoDescuentoRequest(
            @NotNull @Size(min = 1, max = 100) String nombre,
            @NotNull String tipo,
            @NotNull @DecimalMin("0.0") BigDecimal valor
    ) {}
}
