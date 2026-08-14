package com.staffly.backend.payroll.decorator;

import com.staffly.backend.payroll.ConceptoDescuento;
import com.staffly.backend.payroll.TipoConcepto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiscountChainTest {

    // ── cadena vacía ──────────────────────────────────────────────────────────

    @Test
    void emptyChain_netEqualsGross() {
        SalaryComponent result = DiscountChainBuilder.build(bd(1000), List.of());
        assertEquals(bd(1000), result.getNetAmount());
        assertTrue(result.getDeductions().isEmpty());
    }

    @Test
    void nullConceptos_netEqualsGross() {
        SalaryComponent result = DiscountChainBuilder.build(bd(1000), null);
        assertEquals(bd(1000), result.getNetAmount());
    }

    // ── porcentaje ────────────────────────────────────────────────────────────

    @Test
    void singlePorcentaje_deductsFromGross() {
        var concepto = concepto("Obra Social", TipoConcepto.PORCENTAJE, 10);
        SalaryComponent result = DiscountChainBuilder.build(bd(1000), List.of(concepto));
        // 10% of 1000 = 100, net = 900
        assertEquals(new BigDecimal("900.00"), result.getNetAmount());
        assertEquals(1, result.getDeductions().size());
        assertEquals(new BigDecimal("100.00"), result.getDeductions().get(0).monto());
    }

    @Test
    void twoPorcentajes_eachCalculatedOnOriginalGross() {
        // Invariante clave: cada % se calcula sobre el BRUTO ORIGINAL, no sobre el neto acumulado.
        // gross=1000, 10%=100, 5%=50 → total deducciones=150, neto=850 (NO 855)
        var c1 = concepto("Obra Social", TipoConcepto.PORCENTAJE, 10);
        var c2 = concepto("Sindicato",   TipoConcepto.PORCENTAJE, 5);
        SalaryComponent result = DiscountChainBuilder.build(bd(1000), List.of(c1, c2));
        assertEquals(new BigDecimal("850.00"), result.getNetAmount());
        assertEquals(2, result.getDeductions().size());
        assertEquals(new BigDecimal("100.00"), result.getDeductions().get(0).monto());
        assertEquals(new BigDecimal("50.00"),  result.getDeductions().get(1).monto());
    }

    // ── monto fijo ────────────────────────────────────────────────────────────

    @Test
    void singleMontoFijo_deductsFixed() {
        var concepto = concepto("Adelanto", TipoConcepto.MONTO_FIJO, 300);
        SalaryComponent result = DiscountChainBuilder.build(bd(1000), List.of(concepto));
        assertEquals(new BigDecimal("700"), result.getNetAmount());
        assertEquals(new BigDecimal("300"), result.getDeductions().get(0).monto());
    }

    // ── combinados ────────────────────────────────────────────────────────────

    @Test
    void mixedConceptos_correctNet() {
        // gross=2000, Obra Social 10%=200, Adelanto fijo=500 → net=1300
        var c1 = concepto("Obra Social", TipoConcepto.PORCENTAJE, 10);
        var c2 = concepto("Adelanto",    TipoConcepto.MONTO_FIJO, 500);
        SalaryComponent result = DiscountChainBuilder.build(bd(2000), List.of(c1, c2));
        assertEquals(new BigDecimal("1300.00"), result.getNetAmount());
    }

    @Test
    void deductionLines_preserveOrderAndType() {
        var c1 = concepto("Obra Social", TipoConcepto.PORCENTAJE, 10);
        var c2 = concepto("Adelanto",    TipoConcepto.MONTO_FIJO, 200);
        var c3 = concepto("Sindicato",   TipoConcepto.PORCENTAJE, 3);
        SalaryComponent result = DiscountChainBuilder.build(bd(1000), List.of(c1, c2, c3));

        var lines = result.getDeductions();
        assertEquals(3, lines.size());
        assertEquals("Obra Social", lines.get(0).nombre());
        assertEquals(TipoConcepto.PORCENTAJE, lines.get(0).tipo());
        assertEquals("Adelanto", lines.get(1).nombre());
        assertEquals(TipoConcepto.MONTO_FIJO, lines.get(1).tipo());
        assertEquals("Sindicato", lines.get(2).nombre());
    }

    @Test
    void porcentajeRoundsHalfUp() {
        // 3% of 1001 = 30.03 → 30.03 (2 decimal places, HALF_UP)
        var concepto = concepto("Imp", TipoConcepto.PORCENTAJE, 3);
        SalaryComponent result = DiscountChainBuilder.build(new BigDecimal("1001"), List.of(concepto));
        BigDecimal deduction = result.getDeductions().get(0).monto();
        assertEquals(new BigDecimal("30.03"), deduction);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ConceptoDescuento concepto(String nombre, TipoConcepto tipo, int valor) {
        return new ConceptoDescuento(nombre, tipo, BigDecimal.valueOf(valor));
    }

    private static BigDecimal bd(int value) {
        return BigDecimal.valueOf(value);
    }
}
