package com.staffly.backend.payslip;

import com.staffly.backend.employee.Employee;
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payslip.builder.PayslipCalculation;

import java.util.UUID;

/**
 * Factory Method para crear Payslip en sus dos variantes:
 * recibo normal y recibo de ajuste vinculado a un original anulado.
 */
public final class PayslipFactory {

    private PayslipFactory() {}

    public static Payslip normal(UUID companyId, Employee employee,
                                  PayrollPeriod period, PayslipCalculation calc) {
        return build(companyId, employee, period, calc, TipoRecibo.NORMAL, null);
    }

    public static Payslip adjustment(UUID companyId, Employee employee,
                                      PayrollPeriod period, PayslipCalculation calc,
                                      UUID originalPayslipId) {
        return build(companyId, employee, period, calc, TipoRecibo.AJUSTE, originalPayslipId);
    }

    private static Payslip build(UUID companyId, Employee employee, PayrollPeriod period,
                                  PayslipCalculation calc, TipoRecibo tipo, UUID originalId) {
        Payslip p = new Payslip();
        p.setCompanyId(companyId);
        p.setEmployee(employee);
        p.setPayrollPeriod(period);
        p.setEstado(EstadoRecibo.GENERADO);
        p.setTipo(tipo);
        p.setPayslipOriginalId(originalId);

        p.setSueldoBase(calc.sueldoBase());
        p.setHorasNormales(calc.horasNormales());
        p.setHorasExtra(calc.horasExtra());
        p.setHorasFeriado(calc.horasFeriado());
        p.setBrutoCalculado(calc.brutoCalculado());
        p.setTotalDeducciones(calc.totalDeducciones());
        p.setTotalAdelantos(calc.totalAdelantos());
        p.setNetoFinal(calc.netoFinal());
        p.setDetalleDescuentos(calc.deducciones());
        p.setAdelantosAplicados(calc.adelantosAplicados());

        return p;
    }
}
