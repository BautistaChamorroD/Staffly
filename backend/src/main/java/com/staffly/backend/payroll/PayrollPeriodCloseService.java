package com.staffly.backend.payroll;

import com.staffly.backend.advance.Advance;
import com.staffly.backend.advance.AdvanceRepository;
import com.staffly.backend.advance.EstadoAdelanto;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.employee.EstadoLaboral;
import com.staffly.backend.employee.EstadoLiquidacion;
import com.staffly.backend.holiday.HolidayRepository;
import com.staffly.backend.leave.EstadoLicencia;
import com.staffly.backend.leave.LeaveRequest;
import com.staffly.backend.leave.LeaveRequestRepository;
import com.staffly.backend.payroll.dto.PayrollPeriodResponse;
import com.staffly.backend.payslip.EstadoRecibo;
import com.staffly.backend.payslip.Payslip;
import com.staffly.backend.payslip.PayslipFactory;
import com.staffly.backend.payslip.PayslipRepository;
import com.staffly.backend.payslip.builder.PayslipCalculation;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PayrollPeriodCloseService extends PayrollCloseTemplate {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollConfigRepository configRepository;
    private final EmployeeRepository      employeeRepository;
    private final ScheduleRepository      scheduleRepository;
    private final LeaveRequestRepository  leaveRequestRepository;
    private final HolidayRepository       holidayRepository;
    private final AdvanceRepository       advanceRepository;
    private final PayslipRepository       payslipRepository;

    public PayrollPeriodCloseService(PayrollPeriodRepository periodRepository,
                                     PayrollConfigRepository configRepository,
                                     EmployeeRepository employeeRepository,
                                     ScheduleRepository scheduleRepository,
                                     LeaveRequestRepository leaveRequestRepository,
                                     HolidayRepository holidayRepository,
                                     AdvanceRepository advanceRepository,
                                     PayslipRepository payslipRepository) {
        this.periodRepository      = periodRepository;
        this.configRepository      = configRepository;
        this.employeeRepository    = employeeRepository;
        this.scheduleRepository    = scheduleRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayRepository     = holidayRepository;
        this.advanceRepository     = advanceRepository;
        this.payslipRepository     = payslipRepository;
    }

    @Override
    protected PayrollPeriod validateAndLoadPeriod(UUID periodId, UUID companyId) {
        PayrollPeriod period = periodRepository.findByIdAndCompanyId(periodId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el período"));
        if (period.getEstado() == EstadoPeriodo.CERRADO) {
            throw new ConflictException("El período ya está cerrado");
        }
        return period;
    }

    @Override
    protected PayrollConfig loadConfig(UUID companyId) {
        return configRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException(
                        "La empresa no tiene configuración de liquidación — configurala antes de cerrar el período"));
    }

    @Override
    protected List<LocalDate> loadHolidays(PayrollPeriod period, UUID companyId) {
        return holidayRepository
                .findApplicableFechasInRange(companyId, period.getFechaInicio(), period.getFechaFin());
    }

    @Override
    protected List<Employee> collectEmployees(UUID companyId) {
        return employeeRepository.findByCompanyIdForPayroll(companyId, EstadoLaboral.BAJA, EstadoLiquidacion.PENDIENTE);
    }

    @Override
    protected List<Schedule> loadSchedules(UUID employeeId, PayrollPeriod period, UUID companyId) {
        return scheduleRepository.findByCompanyIdAndEmployeeIdInRange(
                companyId, employeeId,
                period.getFechaInicio().atStartOfDay(),
                period.getFechaFin().atTime(23, 59, 59));
    }

    @Override
    protected List<LeaveRequest> loadApprovedLeaves(UUID employeeId, UUID companyId) {
        return leaveRequestRepository.findByCompanyIdAndEmployeeIdAndEstado(
                companyId, employeeId, EstadoLicencia.APROBADA);
    }

    @Override
    protected List<Advance> collectAndMarkAdvances(UUID employeeId, UUID companyId) {
        List<Advance> advances = advanceRepository.findByCompanyIdAndEmployeeIdAndEstado(
                companyId, employeeId, EstadoAdelanto.PENDIENTE);
        advances.forEach(a -> a.setEstado(EstadoAdelanto.DESCONTADO));
        return advances;
    }

    @Override
    protected void persistPayslip(UUID companyId, Employee emp, PayrollPeriod period, PayslipCalculation calc) {
        Payslip payslip = PayslipFactory.normal(companyId, emp, period, calc);
        payslip.setEstado(EstadoRecibo.PAGADO);
        payslip.setFechaPago(LocalDate.now());
        payslipRepository.save(payslip);
    }

    @Override
    protected void markEmployeeUpToDate(Employee emp) {
        emp.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
    }

    @Override
    protected PayrollPeriodResponse finalizePeriod(PayrollPeriod period) {
        period.setEstado(EstadoPeriodo.CERRADO);
        period.setFechaCierre(LocalDate.now());
        return PayrollPeriodResponse.from(periodRepository.save(period));
    }
}
