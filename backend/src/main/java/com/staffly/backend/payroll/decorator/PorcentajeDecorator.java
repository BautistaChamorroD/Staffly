package com.staffly.backend.payroll.decorator;

import com.staffly.backend.payroll.TipoConcepto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Deduce un porcentaje calculado siempre sobre el bruto original. */
public class PorcentajeDecorator extends SalaryDeductionDecorator {

    private final BigDecimal porcentaje;

    public PorcentajeDecorator(SalaryComponent wrapped, BigDecimal grossAmount,
                               String nombre, BigDecimal porcentaje) {
        super(wrapped, grossAmount, nombre);
        this.porcentaje = porcentaje;
    }

    @Override
    protected BigDecimal calculateDeductionAmount() {
        return grossAmount
                .multiply(porcentaje)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    protected DeduccionLinea buildLine() {
        return new DeduccionLinea(nombre, TipoConcepto.PORCENTAJE, calculateDeductionAmount());
    }
}
