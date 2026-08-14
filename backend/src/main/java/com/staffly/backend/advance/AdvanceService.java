package com.staffly.backend.advance;

import com.staffly.backend.advance.dto.AdvanceResponse;
import com.staffly.backend.advance.dto.CreateAdvanceRequest;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdvanceService {

    private final AdvanceRepository advanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public AdvanceService(AdvanceRepository advanceRepository,
                          EmployeeRepository employeeRepository,
                          UserRepository userRepository) {
        this.advanceRepository = advanceRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AdvanceResponse> list(UUID employeeIdFilter, EstadoAdelanto estadoFilter,
                                      StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        List<Advance> advances;

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmpId = resolveOwnEmployeeId(principal);
            advances = advanceRepository.findByCompanyIdAndEmployeeId(companyId, ownEmpId);
        } else if (employeeIdFilter != null) {
            advances = advanceRepository.findByCompanyIdAndEmployeeId(companyId, employeeIdFilter);
        } else {
            advances = advanceRepository.findByCompanyId(companyId);
        }

        return advances.stream()
                .filter(a -> estadoFilter == null || a.getEstado() == estadoFilter)
                .map(AdvanceResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdvanceResponse getById(UUID id, StafflyUserPrincipal principal) {
        Advance advance = findVisibleOrThrow(id, principal);
        return AdvanceResponse.from(advance);
    }

    @Transactional
    public AdvanceResponse create(CreateAdvanceRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        Employee employee = resolveEmployee(request.employeeId(), principal);

        Advance advance = new Advance();
        advance.setCompanyId(companyId);
        advance.setEmployee(employee);
        advance.setFecha(request.fecha());
        advance.setMonto(request.monto());
        advance.setMotivo(request.motivo());
        advance.setEstado(EstadoAdelanto.PENDIENTE);

        return AdvanceResponse.from(advanceRepository.save(advance));
    }

    @Transactional
    public void delete(UUID id, StafflyUserPrincipal principal) {
        Advance advance = advanceRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el adelanto"));

        if (advance.getEstado() == EstadoAdelanto.DESCONTADO) {
            throw new ConflictException("No se puede eliminar un adelanto ya descontado en una liquidación");
        }
        if (advance.getEstado() == EstadoAdelanto.CANCELADO) {
            throw new BadRequestException("El adelanto ya fue cancelado");
        }

        advanceRepository.delete(advance);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Employee resolveEmployee(UUID requestedEmployeeId, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        if (principal.getRol() == Rol.EMPLOYEE) {
            return userRepository.findByIdAndCompanyId(principal.getUserId(), companyId)
                    .map(u -> {
                        if (u.getEmployee() == null) {
                            throw new BadRequestException("El usuario no tiene un empleado asociado");
                        }
                        return u.getEmployee();
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        }
        if (requestedEmployeeId == null) {
            throw new BadRequestException("Debe especificar el empleado para el adelanto");
        }
        return employeeRepository.findByIdAndCompanyId(requestedEmployeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado"));
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

    private Advance findVisibleOrThrow(UUID id, StafflyUserPrincipal principal) {
        Advance advance = advanceRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el adelanto"));

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmpId = resolveOwnEmployeeId(principal);
            if (!advance.getEmployeeId().equals(ownEmpId)) {
                throw new ResourceNotFoundException("No se encontró el adelanto");
            }
        }
        return advance;
    }
}
