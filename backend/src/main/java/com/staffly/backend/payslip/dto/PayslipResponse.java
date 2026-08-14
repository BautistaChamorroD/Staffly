package com.staffly.backend.payslip.dto;

import com.staffly.backend.payroll.decorator.DeduccionLinea;
import com.staffly.backend.payslip.EstadoRecibo;
import com.staffly.backend.payslip.Payslip;
import com.staffly.backend.payslip.TipoRecibo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PayslipResponse(
        UUID id,
        UUID employeeId,
        String employeeNombre,
        String employeeApellido,
        UUID payrollPeriodId,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        EstadoRecibo estado,
        TipoRecibo tipo,
        UUID payslipOriginalId,
        BigDecimal sueldoBase,
        BigDecimal horasNormales,
        BigDecimal horasExtra,
        BigDecimal horasFeriado,
        BigDecimal brutoCalculado,
        List<DeduccionLinea> detalleDescuentos,
        BigDecimal totalDeducciones,
        BigDecimal totalAdelantos,
        BigDecimal netoFinal,
        LocalDate fechaPago
) {
    public static PayslipResponse from(Payslip p) {
        return new PayslipResponse(
                p.getId(),
                p.getEmployeeId(),
                p.getEmployee().getNombre(),
                p.getEmployee().getApellido(),
                p.getPayrollPeriod().getId(),
                p.getPayrollPeriod().getFechaInicio(),
                p.getPayrollPeriod().getFechaFin(),
                p.getEstado(),
                p.getTipo(),
                p.getPayslipOriginalId(),
                p.getSueldoBase(),
                p.getHorasNormales(),
                p.getHorasExtra(),
                p.getHorasFeriado(),
                p.getBrutoCalculado(),
                p.getDetalleDescuentos(),
                p.getTotalDeducciones(),
                p.getTotalAdelantos(),
                p.getNetoFinal(),
                p.getFechaPago()
        );
    }
}
