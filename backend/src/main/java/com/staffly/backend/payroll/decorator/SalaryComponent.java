package com.staffly.backend.payroll.decorator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Componente base del patrón Decorator para el cálculo del neto salarial.
 * Cada implementación concreta agrega (o no) una deducción sobre el bruto.
 */
public interface SalaryComponent {
    /** Monto neto después de aplicar todas las deducciones acumuladas hasta este punto. */
    BigDecimal getNetAmount();

    /** Lista de líneas de deducción aplicadas hasta este punto, en orden de aplicación. */
    List<DeduccionLinea> getDeductions();
}
