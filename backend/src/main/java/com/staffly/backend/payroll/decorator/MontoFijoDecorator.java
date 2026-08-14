package com.staffly.backend.payroll.decorator;

import com.staffly.backend.payroll.TipoConcepto;

import java.math.BigDecimal;

/** Deduce un monto fijo absoluto. */
public class MontoFijoDecorator extends SalaryDeductionDecorator {

    private final BigDecimal monto;

    public MontoFijoDecorator(SalaryComponent wrapped, BigDecimal grossAmount,
                              String nombre, BigDecimal monto) {
        super(wrapped, grossAmount, nombre);
        this.monto = monto;
    }

    @Override
    protected BigDecimal calculateDeductionAmount() {
        return monto;
    }

    @Override
    protected DeduccionLinea buildLine() {
        return new DeduccionLinea(nombre, TipoConcepto.MONTO_FIJO, monto);
    }
}
