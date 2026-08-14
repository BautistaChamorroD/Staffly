package com.staffly.backend.payroll.decorator;

import com.staffly.backend.payroll.ConceptoDescuento;
import com.staffly.backend.payroll.TipoConcepto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Construye la cadena de decoradores a partir del bruto y la lista de
 * {@link ConceptoDescuento} configurados en {@code PayrollConfig}.
 */
public final class DiscountChainBuilder {

    private DiscountChainBuilder() {}

    public static SalaryComponent build(BigDecimal grossAmount, List<ConceptoDescuento> conceptos) {
        SalaryComponent component = new GrossSalaryComponent(grossAmount);
        if (conceptos == null) return component;

        for (ConceptoDescuento concepto : conceptos) {
            if (concepto.getTipo() == TipoConcepto.PORCENTAJE) {
                component = new PorcentajeDecorator(component, grossAmount,
                        concepto.getNombre(), concepto.getValor());
            } else {
                component = new MontoFijoDecorator(component, grossAmount,
                        concepto.getNombre(), concepto.getValor());
            }
        }
        return component;
    }
}
