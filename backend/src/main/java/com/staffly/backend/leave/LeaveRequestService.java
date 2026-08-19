package com.staffly.backend.leave;

import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.LeaveApprovalConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.leave.dto.CreateLeaveRequestRequest;
import com.staffly.backend.leave.dto.LeaveRequestResponse;
import com.staffly.backend.leave.dto.RejectLeaveRequestRequest;
import com.staffly.backend.schedule.Schedule;
import com.staffly.backend.schedule.ScheduleRepository;
import com.staffly.backend.schedule.dto.ScheduleResponse;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;

    public LeaveRequestService(
            LeaveRequestRepository leaveRequestRepository,
            LeaveTypeRepository leaveTypeRepository,
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            ScheduleRepository scheduleRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> list(UUID employeeIdFilter, EstadoLicencia estadoFilter,
                                           StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        List<LeaveRequest> requests;

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID empId = resolveOwnEmployeeId(principal);
            requests = leaveRequestRepository.findByCompanyIdAndEmployeeIdWithDetails(companyId, empId);
        } else {
            requests = leaveRequestRepository.findByCompanyIdWithDetails(companyId);
        }

        return requests.stream()
                .filter(lr -> supervisorFilter(lr, principal))
                .filter(lr -> employeeIdFilter == null || lr.getEmployeeId().equals(employeeIdFilter))
                .filter(lr -> estadoFilter == null || lr.getEstado() == estadoFilter)
                .sorted(java.util.Comparator.comparing(LeaveRequest::getFechaInicio))
                .map(LeaveRequestResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(UUID id, StafflyUserPrincipal principal) {
        LeaveRequest lr = findVisibleOrThrow(id, principal);
        return LeaveRequestResponse.from(lr);
    }

    @Transactional
    public LeaveRequestResponse create(CreateLeaveRequestRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();

        Employee employee = resolveEmployee(request.employeeId(), principal);
        LeaveType leaveType = leaveTypeRepository.findByIdAndCompanyId(request.leaveTypeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el tipo de licencia solicitado"));

        if (!request.fechaFin().isAfter(request.fechaInicio()) && !request.fechaFin().isEqual(request.fechaInicio())) {
            throw new BadRequestException("La fecha de fin debe ser igual o posterior a la fecha de inicio");
        }
        if (request.fechaFin().isBefore(request.fechaInicio())) {
            throw new BadRequestException("La fecha de fin debe ser igual o posterior a la fecha de inicio");
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setCompanyId(companyId);
        lr.setEmployee(employee);
        lr.setLeaveType(leaveType);
        lr.setFechaInicio(request.fechaInicio());
        lr.setFechaFin(request.fechaFin());
        lr.setMotivo(request.motivo());
        lr.setEstado(EstadoLicencia.PENDIENTE);
        return LeaveRequestResponse.from(leaveRequestRepository.save(lr));
    }

    @Transactional
    public LeaveRequestResponse approve(UUID id, StafflyUserPrincipal principal) {
        LeaveRequest lr = findVisibleOrThrow(id, principal);

        if (lr.getEstado() != EstadoLicencia.PENDIENTE) {
            throw new BadRequestException("Solo se pueden aprobar solicitudes en estado PENDIENTE");
        }

        // verificar solapamiento con turnos existentes antes de confirmar
        LocalDateTime inicio = lr.getFechaInicio().atStartOfDay();
        LocalDateTime fin = lr.getFechaFin().plusDays(1).atStartOfDay();
        List<Schedule> conflictos = scheduleRepository.findAllConflictingInRange(
                principal.getCompanyId(), lr.getEmployeeId(), inicio, fin);

        if (!conflictos.isEmpty()) {
            throw new LeaveApprovalConflictException(
                    conflictos.stream().map(ScheduleResponse::from).toList());
        }

        lr.setEstado(EstadoLicencia.APROBADA);
        lr.setAprobadoPor(principal.getUserId());
        return LeaveRequestResponse.from(leaveRequestRepository.save(lr));
    }

    @Transactional
    public LeaveRequestResponse reject(UUID id, RejectLeaveRequestRequest request, StafflyUserPrincipal principal) {
        LeaveRequest lr = findVisibleOrThrow(id, principal);

        if (lr.getEstado() != EstadoLicencia.PENDIENTE) {
            throw new BadRequestException("Solo se pueden rechazar solicitudes en estado PENDIENTE");
        }

        lr.setEstado(EstadoLicencia.RECHAZADA);
        lr.setMotivoRechazo(request.motivo());
        return LeaveRequestResponse.from(leaveRequestRepository.save(lr));
    }

    @Transactional
    public LeaveRequestResponse cancel(UUID id, StafflyUserPrincipal principal) {
        LeaveRequest lr = findVisibleOrThrow(id, principal);

        if (lr.getEstado() == EstadoLicencia.RECHAZADA || lr.getEstado() == EstadoLicencia.CANCELADA) {
            throw new BadRequestException("No se puede cancelar una solicitud " + lr.getEstado().name().toLowerCase());
        }

        if (lr.getEstado() == EstadoLicencia.APROBADA && !lr.getFechaInicio().isAfter(LocalDate.now())) {
            throw new BadRequestException("No se puede cancelar una licencia aprobada que ya comenzó");
        }

        // EMPLOYEE solo puede cancelar la propia
        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmpId = resolveOwnEmployeeId(principal);
            if (!lr.getEmployeeId().equals(ownEmpId)) {
                throw new ResourceNotFoundException("No se encontró la solicitud de licencia");
            }
        }

        lr.setEstado(EstadoLicencia.CANCELADA);
        return LeaveRequestResponse.from(leaveRequestRepository.save(lr));
    }

    // ── helpers privados ──────────────────────────────────────────────────────

    /**
     * Resuelve el Employee para la creación. EMPLOYEE solo puede crear para sí mismo
     * (se ignora el employeeId del body y se usa el vinculado a su User). ADMIN/RRHH
     * deben proveer un employeeId válido.
     */
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
            throw new BadRequestException("Debe especificar el empleado para esta solicitud");
        }
        return employeeRepository.findByIdAndCompanyId(requestedEmployeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado solicitado"));
    }

    /**
     * Obtiene el employeeId del empleado vinculado al usuario EMPLOYEE.
     * Usado en list y cancel para restricción de visibilidad.
     */
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

    /**
     * Busca la solicitud teniendo en cuenta el scope del principal.
     * Devuelve 404 si no existe o si el principal no tiene acceso (política 404 > 403).
     */
    private LeaveRequest findVisibleOrThrow(UUID id, StafflyUserPrincipal principal) {
        LeaveRequest lr = leaveRequestRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la solicitud de licencia"));

        // inicializar la colección de branches para la comprobación de SUPERVISOR
        lr.getEmployee().getBranches().size();

        if (principal.getRol() == Rol.SUPERVISOR && !supervisorFilter(lr, principal)) {
            throw new ResourceNotFoundException("No se encontró la solicitud de licencia");
        }

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmpId = resolveOwnEmployeeId(principal);
            if (!lr.getEmployeeId().equals(ownEmpId)) {
                throw new ResourceNotFoundException("No se encontró la solicitud de licencia");
            }
        }

        return lr;
    }

    /** Para SUPERVISOR: solo solicitudes de empleados en sus sucursales. Otros roles: sin filtro. */
    private boolean supervisorFilter(LeaveRequest lr, StafflyUserPrincipal principal) {
        if (principal.getRol() != Rol.SUPERVISOR) return true;
        return lr.getEmployee().getBranches().stream()
                .anyMatch(b -> principal.getBranchIds().contains(b.getId()));
    }
}
