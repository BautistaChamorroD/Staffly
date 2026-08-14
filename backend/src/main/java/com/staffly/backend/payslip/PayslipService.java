package com.staffly.backend.payslip;

import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.payslip.dto.MarkPaidRequest;
import com.staffly.backend.payslip.dto.PayslipResponse;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.UserRepository;
import com.staffly.backend.common.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final UserRepository    userRepository;

    public PayslipService(PayslipRepository payslipRepository, UserRepository userRepository) {
        this.payslipRepository = payslipRepository;
        this.userRepository    = userRepository;
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

        payslip.setEstado(EstadoRecibo.PAGADO);
        payslip.setFechaPago(request != null && request.fechaPago() != null
                ? request.fechaPago()
                : LocalDate.now());

        return PayslipResponse.from(payslipRepository.save(payslip));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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
