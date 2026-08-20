package com.staffly.backend.payslip;

import com.staffly.backend.advance.Advance;
import com.staffly.backend.advance.AdvanceRepository;
import com.staffly.backend.advance.EstadoAdelanto;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.common.UnprocessableEntityException;
import com.staffly.backend.common.audit.AuditableFieldChangedEvent;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.holiday.HolidayRepository;
import com.staffly.backend.leave.EstadoLicencia;
import com.staffly.backend.leave.LeaveRequest;
import com.staffly.backend.leave.LeaveRequestRepository;
import com.staffly.backend.payroll.PayrollConfig;
import com.staffly.backend.payroll.PayrollConfigRepository;
import com.staffly.backend.payroll.PayrollPeriod;
import com.staffly.backend.payslip.builder.PayslipBuilder;
import com.staffly.backend.payslip.builder.PayslipCalculation;
import com.staffly.backend.payslip.dto.MarkPaidRequest;
import com.staffly.backend.payslip.dto.PayslipResponse;
import com.staffly.backend.payslip.dto.VoidPayslipRequest;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.ScheduleRepository;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PayslipService {

    /** Discriminador de Payslip en la tabla genérica audit_log. */
    private static final String AUDIT_ENTITY_TYPE = "PAYSLIP";

    private final PayslipRepository       payslipRepository;
    private final UserRepository          userRepository;
    private final PayrollConfigRepository configRepository;
    private final ScheduleRepository      scheduleRepository;
    private final LeaveRequestRepository  leaveRequestRepository;
    private final HolidayRepository       holidayRepository;
    private final AdvanceRepository       advanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PayslipService(PayslipRepository payslipRepository,
                          UserRepository userRepository,
                          PayrollConfigRepository configRepository,
                          ScheduleRepository scheduleRepository,
                          LeaveRequestRepository leaveRequestRepository,
                          HolidayRepository holidayRepository,
                          AdvanceRepository advanceRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.payslipRepository     = payslipRepository;
        this.userRepository        = userRepository;
        this.configRepository      = configRepository;
        this.scheduleRepository    = scheduleRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayRepository     = holidayRepository;
        this.advanceRepository     = advanceRepository;
        this.eventPublisher        = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<PayslipResponse> list(UUID employeeIdFilter, UUID payrollPeriodIdFilter,
                                      EstadoRecibo estadoFilter, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        List<Payslip> payslips;

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmpId = resolveOwnEmployeeId(principal);
            payslips = payslipRepository.findByCompanyIdAndEmployeeId(companyId, ownEmpId);
        } else if (employeeIdFilter != null) {
            payslips = payslipRepository.findByCompanyIdAndEmployeeId(companyId, employeeIdFilter);
        } else {
            payslips = payslipRepository.findByCompanyId(companyId);
        }

        return payslips.stream()
                .filter(p -> payrollPeriodIdFilter == null
                        || p.getPayrollPeriod().getId().equals(payrollPeriodIdFilter))
                .filter(p -> estadoFilter == null || p.getEstado() == estadoFilter)
                .map(PayslipResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayslipResponse getById(UUID id, StafflyUserPrincipal principal) {
        return PayslipResponse.from(findVisibleOrThrow(id, principal));
    }

    @Transactional
    public PayslipResponse markPaid(UUID id, MarkPaidRequest request, StafflyUserPrincipal principal) {
        Payslip payslip = payslipRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el recibo"));

        PayslipStateTransition.validate(payslip.getEstado(), EstadoRecibo.PAGADO);

        String estadoAnterior = payslip.getEstado().name();
        payslip.setEstado(EstadoRecibo.PAGADO);
        payslip.setFechaPago(request != null && request.fechaPago() != null
                ? request.fechaPago()
                : LocalDate.now());

        Payslip saved = payslipRepository.save(payslip);
        publishFieldChange(saved, principal, "estado", estadoAnterior, EstadoRecibo.PAGADO.name());
        return PayslipResponse.from(saved);
    }

    @Transactional
    public PayslipResponse voidAndAdjust(UUID id, VoidPayslipRequest request,
                                         StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();

        Payslip original = payslipRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el recibo"));

        if (original.getEstado() != EstadoRecibo.PAGADO) {
            throw new UnprocessableEntityException(
                    "Solo se puede anular un recibo en estado PAGADO");
        }

        original.setEstado(EstadoRecibo.ANULADO);
        original.setMotivoAnulacion(request != null ? request.motivoAnulacion() : null);
        payslipRepository.save(original);
        publishFieldChange(original, principal, "estado", EstadoRecibo.PAGADO.name(), EstadoRecibo.ANULADO.name());

        Employee      employee = original.getEmployee();
        PayrollPeriod period   = original.getPayrollPeriod();

        PayrollConfig config = configRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new BadRequestException(
                        "La empresa no tiene configuración de liquidación"));

        List<LocalDate> holidays = holidayRepository
                .findApplicableFechasInRange(companyId, period.getFechaInicio(), period.getFechaFin());

        List<Schedule> schedules = scheduleRepository.findByCompanyIdAndEmployeeIdInRange(
                companyId, employee.getId(),
                period.getFechaInicio().atStartOfDay(),
                period.getFechaFin().atTime(23, 59, 59));

        List<LeaveRequest> leaves = leaveRequestRepository.findByCompanyIdAndEmployeeIdAndEstado(
                companyId, employee.getId(), EstadoLicencia.APROBADA);

        List<Advance> advances = advanceRepository.findByCompanyIdAndEmployeeIdAndEstado(
                companyId, employee.getId(), EstadoAdelanto.PENDIENTE);

        PayslipCalculation calc = new PayslipBuilder()
                .withEmployee(employee)
                .withPeriod(period)
                .withConfig(config)
                .withSchedules(schedules)
                .withHolidays(holidays)
                .withLeaveRequests(leaves)
                .withAdvances(advances)
                .build();

        Payslip adjustment = PayslipFactory.adjustment(companyId, employee, period, calc, id);
        return PayslipResponse.from(payslipRepository.save(adjustment));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void publishFieldChange(
            Payslip payslip, StafflyUserPrincipal principal, String campo, String valorAnterior, String valorNuevo) {
        eventPublisher.publishEvent(new AuditableFieldChangedEvent(
                principal.getCompanyId(), AUDIT_ENTITY_TYPE, payslip.getId(),
                principal.getUserId(), campo, valorAnterior, valorNuevo));
    }

    private Payslip findVisibleOrThrow(UUID id, StafflyUserPrincipal principal) {
        Payslip p = payslipRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el recibo"));

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmpId = resolveOwnEmployeeId(principal);
            if (!p.getEmployeeId().equals(ownEmpId)) {
                throw new ResourceNotFoundException("No se encontró el recibo");
            }
        }
        return p;
    }

    private UUID resolveOwnEmployeeId(StafflyUserPrincipal principal) {
        return userRepository.findByIdAndCompanyId(principal.getUserId(), principal.getCompanyId())
                .map(u -> {
                    if (u.getEmployee() == null) {
                        throw new BadRequestException("El usuario no tiene un empleado asociado");
                    }
                    return u.getEmployee().getId();
                })
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
