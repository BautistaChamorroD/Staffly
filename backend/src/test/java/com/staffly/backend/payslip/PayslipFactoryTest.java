package com.staffly.backend.payslip;

import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.employee.TipoContrato;
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payslip.builder.PayslipCalculation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PayslipFactoryTest {

    private Employee     employee;
    private PayrollPeriod period;
    private PayslipCalculation calc;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();

        employee = new Employee();
        employee.setCompanyId(companyId);
        employee.setNombre("Ana"); employee.setApellido("López");
        employee.setDocumento("87654321");
        employee.setFechaNacimiento(LocalDate.of(1992, 5, 10));
        employee.setFechaIngreso(LocalDate.of(2023, 3, 1));
        employee.setSueldoBase(BigDecimal.valueOf(150_000));
        employee.setTipoContrato(TipoContrato.JORNADA_COMPLETA);
        employee.setCategoria("Operario");
        employee.setEstadoLaboral(EstadoLaboral.ACTIVO);
        employee.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);

        period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setFechaInicio(LocalDate.of(2026, 8, 1));
        period.setFechaFin(LocalDate.of(2026, 8, 31));

        calc = new PayslipCalculation(
                null, null,
                BigDecimal.valueOf(150_000),
                BigDecimal.valueOf(750),
                BigDecimal.valueOf(160), BigDecimal.valueOf(4), BigDecimal.ZERO,
                BigDecimal.valueOf(123_000), BigDecimal.ZERO, BigDecimal.valueOf(123_000),
                List.of(), BigDecimal.ZERO,
                List.of(), BigDecimal.ZERO,
                BigDecimal.valueOf(123_000)
        );
    }

    @Test
    void normalFactory_createsGeneradoNormal() {
        Payslip p = PayslipFactory.normal(companyId, employee, period, calc);

        assertEquals(EstadoRecibo.GENERADO, p.getEstado());
        assertEquals(TipoRecibo.NORMAL,     p.getTipo());
        assertNull(p.getPayslipOriginalId());
        assertEquals(companyId,               p.getCompanyId());
        assertEquals(employee,                p.getEmployee());
        assertEquals(period,                  p.getPayrollPeriod());
        assertEquals(BigDecimal.valueOf(150_000), p.getSueldoBase());
        assertEquals(BigDecimal.valueOf(123_000), p.getNetoFinal());
    }

    @Test
    void adjustmentFactory_linksToOriginal() {
        UUID originalId = UUID.randomUUID();
        Payslip p = PayslipFactory.adjustment(companyId, employee, period, calc, originalId);

        assertEquals(EstadoRecibo.GENERADO, p.getEstado());
        assertEquals(TipoRecibo.AJUSTE,     p.getTipo());
        assertEquals(originalId,             p.getPayslipOriginalId());
    }

    @Test
    void normalFactory_snapshotIsComplete() {
        Payslip p = PayslipFactory.normal(companyId, employee, period, calc);

        assertEquals(BigDecimal.valueOf(160),     p.getHorasNormales());
        assertEquals(BigDecimal.valueOf(4),       p.getHorasExtra());
        assertEquals(BigDecimal.ZERO,             p.getHorasFeriado());
        assertEquals(BigDecimal.valueOf(123_000), p.getBrutoCalculado());
        assertEquals(BigDecimal.ZERO,             p.getTotalDeducciones());
        assertEquals(BigDecimal.ZERO,             p.getTotalAdelantos());
        assertNotNull(p.getDetalleDescuentos());
        assertNotNull(p.getAdelantosAplicados());
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @Test
    void stateTransition_generadoToPagadoIsValid() {
        assertDoesNotThrow(() ->
                PayslipStateTransition.validate(EstadoRecibo.GENERADO, EstadoRecibo.PAGADO));
    }

    @Test
    void stateTransition_pagadoToAnuladoIsValid() {
        assertDoesNotThrow(() ->
                PayslipStateTransition.validate(EstadoRecibo.PAGADO, EstadoRecibo.ANULADO));
    }

    @Test
    void stateTransition_generadoToAnuladoIsInvalid() {
        assertThrows(Exception.class, () ->
                PayslipStateTransition.validate(EstadoRecibo.GENERADO, EstadoRecibo.ANULADO));
    }

    @Test
    void stateTransition_anuladoToAnyIsInvalid() {
        assertThrows(Exception.class, () ->
                PayslipStateTransition.validate(EstadoRecibo.ANULADO, EstadoRecibo.PAGADO));
        assertThrows(Exception.class, () ->
                PayslipStateTransition.validate(EstadoRecibo.ANULADO, EstadoRecibo.GENERADO));
    }
}
