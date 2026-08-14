package com.staffly.backend.payslip.pdf;

import com.staffly.backend.payroll.decorator.DeduccionLinea;
import com.staffly.backend.payslip.EstadoRecibo;
import com.staffly.backend.payslip.TipoRecibo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PayslipPdfData(
        String        companyNombre,
        String        companyRazonSocial,
        String        employeeNombre,
        String        employeeApellido,
        String        employeeDocumento,
        String        employeeCategoria,
        LocalDate     periodoInicio,
        LocalDate     periodoFin,
        UUID          payslipId,
        EstadoRecibo  estado,
        TipoRecibo    tipo,
        UUID          payslipOriginalId,
        BigDecimal    sueldoBase,
        BigDecimal    horasNormales,
        BigDecimal    horasExtra,
        BigDecimal    horasFeriado,
        BigDecimal    brutoCalculado,
        List<DeduccionLinea> detalleDescuentos,
        BigDecimal    totalDeducciones,
        BigDecimal    totalAdelantos,
        BigDecimal    netoFinal,
        LocalDate     fechaPago,
        LocalDate     fechaEmision
) {}
