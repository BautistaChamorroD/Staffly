package com.staffly.backend.payroll.decorator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorador abstracto que envuelve un {@link SalaryComponent} y le agrega
 * una deducción. Cada subclase concreta implementa cómo se calcula el monto
 * de la deducción.
 *
 * <p><b>Invariante de diseño:</b> los porcentajes siempre se calculan sobre el
 * <em>bruto original</em> (no sobre el neto acumulado). Por eso se recibe
 * {@code grossAmount} de forma explícita en cada decorador. Esto garantiza que
 * el orden de aplicación de los porcentajes no afecta el resultado.</p>
 */
public abstract class SalaryDeductionDecorator implements SalaryComponent {

    protected final SalaryComponent wrapped;
    protected final BigDecimal grossAmount;
    protected final String nombre;

    protected SalaryDeductionDecorator(SalaryComponent wrapped, BigDecimal grossAmount, String nombre) {
        this.wrapped = wrapped;
        this.grossAmount = grossAmount;
        this.nombre = nombre;
    }

    protected abstract BigDecimal calculateDeductionAmount();
    protected abstract DeduccionLinea buildLine();

    @Override
    public BigDecimal getNetAmount() {
        return wrapped.getNetAmount().subtract(calculateDeductionAmount());
    }

    @Override
    public List<DeduccionLinea> getDeductions() {
        List<DeduccionLinea> result = new ArrayList<>(wrapped.getDeductions());
        result.add(buildLine());
        return result;
    }
}
