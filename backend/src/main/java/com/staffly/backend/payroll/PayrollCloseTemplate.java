package com.staffly.backend.payroll;

import com.staffly.backend.advance.Advance;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.leave.LeaveRequest;
import com.staffly.backend.payroll.dto.PayrollPeriodResponse;
import com.staffly.backend.payslip.builder.PayslipBuilder;
import com.staffly.backend.payslip.builder.PayslipCalculation;
import com.staffly.backend.schedule.Schedule;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Template Method para el cierre de período de nómina.
 *
 * <p>Define el algoritmo fijo de cierre (pasos 1–7) y delega cada paso
 * a métodos abstractos que la subclase concreta implementa con acceso
 * a repositorios y dependencias de Spring.
 */
public abstract class PayrollCloseTemplate {

    /**
     * {@code @Transactional} va acá — sobre el método concreto — y no alcanza con
     * ponerlo a nivel de clase en la subclase: como {@code close()} nunca se
     * sobreescribe, Spring resuelve sus atributos transaccionales contra la clase
     * que lo DECLARA ({@code PayrollCloseTemplate}), no contra la clase en tiempo
     * de ejecución. Sin esto, el lock pesimista de
     * {@code validateAndLoadPeriod()} se libera apenas termina ese método, en vez
     * de mantenerse durante todo el cierre — el propio cierre completo (todos los
     * Payslips + el período) tampoco era atómico (issue #149).
     */
    @Transactional
    public PayrollPeriodResponse close(UUID periodId, UUID companyId) {
        PayrollPeriod        period    = validateAndLoadPeriod(periodId, companyId);
        PayrollConfig        config    = loadConfig(companyId);
        List<LocalDate>      holidays  = loadHolidays(period, companyId);
        List<Employee>       employees = collectEmployees(companyId);

        for (Employee emp : employees) {
            List<Schedule>     schedules = loadSchedules(emp.getId(), period, companyId);
            List<LeaveRequest> leaves    = loadApprovedLeaves(emp.getId(), companyId);
            List<Advance>      advances  = collectAndMarkAdvances(emp.getId(), period, companyId);

            PayslipCalculation calc = new PayslipBuilder()
                    .withEmployee(emp)
                    .withPeriod(period)
                    .withConfig(config)
                    .withSchedules(schedules)
                    .withHolidays(holidays)
                    .withLeaveRequests(leaves)
                    .withAdvances(advances)
                    .build();

            persistPayslip(companyId, emp, period, calc);
            markEmployeeUpToDate(emp);
        }

        return finalizePeriod(period);
    }

    protected abstract PayrollPeriod   validateAndLoadPeriod(UUID periodId, UUID companyId);
    protected abstract PayrollConfig   loadConfig(UUID companyId);
    protected abstract List<LocalDate> loadHolidays(PayrollPeriod period, UUID companyId);
    protected abstract List<Employee>  collectEmployees(UUID companyId);
    protected abstract List<Schedule>  loadSchedules(UUID employeeId, PayrollPeriod period, UUID companyId);
    protected abstract List<LeaveRequest> loadApprovedLeaves(UUID employeeId, UUID companyId);
    protected abstract List<Advance>   collectAndMarkAdvances(UUID employeeId, PayrollPeriod period, UUID companyId);
    protected abstract void            persistPayslip(UUID companyId, Employee emp, PayrollPeriod period, PayslipCalculation calc);
    protected abstract void            markEmployeeUpToDate(Employee emp);
    protected abstract PayrollPeriodResponse finalizePeriod(PayrollPeriod period);
}
