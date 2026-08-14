package com.staffly.backend.payroll.decorator;

import java.math.BigDecimal;
import java.util.List;

/** Componente base: representa el sueldo bruto sin deducciones aplicadas. */
public class GrossSalaryComponent implements SalaryComponent {

    private final BigDecimal grossAmount;

    public GrossSalaryComponent(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    @Override
    public BigDecimal getNetAmount() {
        return grossAmount;
    }

    @Override
    public List<DeduccionLinea> getDeductions() {
        return List.of();
    }
}
