package com.staffly.backend.payroll.strategy;

import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.Periodicidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HoursCalculationStrategyTest {

    private PayrollConfig config;

    @BeforeEach
    void setUp() {
        config = new PayrollConfig();
        config.setCompanyId(java.util.UUID.randomUUID());
        config.setUmbralHorasExtra(BigDecimal.valueOf(8));
        config.setMultiplicadorHoraExtra(BigDecimal.valueOf(1.5));
        config.setMultiplicadorFeriado(BigDecimal.valueOf(2.0));
        config.setPeriodicidad(Periodicidad.MENSUAL);
        config.setConceptosDescuento(List.of());
    }

    // ── StandardHoursStrategy ────────────────────────────────────────────────

    @Test
    void standard_allHoursNormal() {
        var result = new StandardHoursStrategy().calculate(bd(6), bd(0), config);
        assertEquals(bd(6), result.normalHours());
        assertEquals(BigDecimal.ZERO, result.overtimeHours());
        assertEquals(BigDecimal.ZERO, result.holidayHours());
        assertEquals(bd(6), result.total());
    }

    // ── OvertimeStrategy ─────────────────────────────────────────────────────

    @Test
    void overtime_belowThreshold_allNormal() {
        var result = new OvertimeStrategy().calculate(bd(7), bd(0), config);
        assertEquals(bd(7), result.normalHours());
        assertEquals(BigDecimal.ZERO, result.overtimeHours());
    }

    @Test
    void overtime_exactlyAtThreshold_allNormal() {
        var result = new OvertimeStrategy().calculate(bd(8), bd(0), config);
        assertEquals(bd(8), result.normalHours());
        assertEquals(BigDecimal.ZERO, result.overtimeHours());
    }

    @Test
    void overtime_exceedsThreshold_splits() {
        var result = new OvertimeStrategy().calculate(bd(10), bd(0), config);
        assertEquals(bd(8), result.normalHours());
        assertEquals(bd(2), result.overtimeHours());
    }

    @Test
    void overtime_alreadyPartiallyOver_splits() {
        // 6 already counted, 4 hour shift → 2 normal + 2 overtime
        var result = new OvertimeStrategy().calculate(bd(4), bd(6), config);
        assertEquals(bd(2), result.normalHours());
        assertEquals(bd(2), result.overtimeHours());
    }

    @Test
    void overtime_alreadyFullyOver_allOvertime() {
        var result = new OvertimeStrategy().calculate(bd(4), bd(9), config);
        assertEquals(BigDecimal.ZERO, result.normalHours());
        assertEquals(bd(4), result.overtimeHours());
    }

    @Test
    void overtime_totalIsAlwaysShiftHours() {
        var result = new OvertimeStrategy().calculate(bd(5), bd(6), config);
        assertEquals(bd(5), result.total());
    }

    // ── HolidayStrategy ──────────────────────────────────────────────────────

    @Test
    void holiday_allHoursHoliday() {
        var result = new HolidayStrategy().calculate(bd(8), bd(0), config);
        assertEquals(BigDecimal.ZERO, result.normalHours());
        assertEquals(BigDecimal.ZERO, result.overtimeHours());
        assertEquals(bd(8), result.holidayHours());
    }

    @Test
    void holiday_doesNotCheckOvertimeThreshold() {
        // Even with 12 hours already counted, holiday strategy ignores overtime
        var result = new HolidayStrategy().calculate(bd(10), bd(12), config);
        assertEquals(bd(10), result.holidayHours());
        assertEquals(BigDecimal.ZERO, result.overtimeHours());
    }

    // ── Selector ─────────────────────────────────────────────────────────────

    @Test
    void selector_returnsHolidayStrategyOnHolidayDate() {
        LocalDate christmas = LocalDate.of(2026, 12, 25);
        var strategy = HoursCalculationStrategySelector.select(christmas, List.of(christmas));
        assertInstanceOf(HolidayStrategy.class, strategy);
    }

    @Test
    void selector_returnsOvertimeStrategyOnNonHoliday() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        var strategy = HoursCalculationStrategySelector.select(monday, List.of(LocalDate.of(2026, 12, 25)));
        assertInstanceOf(OvertimeStrategy.class, strategy);
    }

    @Test
    void selector_emptyHolidayListReturnsOvertime() {
        var strategy = HoursCalculationStrategySelector.select(LocalDate.of(2026, 8, 3), List.of());
        assertInstanceOf(OvertimeStrategy.class, strategy);
    }

    @Test
    void selector_nullHolidayListReturnsOvertime() {
        var strategy = HoursCalculationStrategySelector.select(LocalDate.of(2026, 8, 3), null);
        assertInstanceOf(OvertimeStrategy.class, strategy);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal bd(int value) {
        return BigDecimal.valueOf(value);
    }
}
