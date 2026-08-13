// backend/src/main/java/com/staffly/backend/schedule/ScheduleService.java
package com.staffly.backend.schedule;

import com.staffly.backend.availability.AvailabilityRepository;
import com.staffly.backend.availability.DiaSemana;
import com.staffly.backend.availability.EmployeeAvailability;
import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.common.audit.AuditableFieldChangedEvent;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeResolver;
import com.staffly.backend.schedule.dto.CreateScheduleRequest;
import com.staffly.backend.schedule.dto.ScheduleResponse;
import com.staffly.backend.schedule.dto.UpdateScheduleRequest;
import com.staffly.backend.schedule.dto.UpdateStatusRequest;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final EmployeeResolver employeeResolver;
    private final BranchRepository branchRepository;
    private final AvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           EmployeeResolver employeeResolver,
                           BranchRepository branchRepository,
                           AvailabilityRepository availabilityRepository,
                           UserRepository userRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.scheduleRepository = scheduleRepository;
        this.employeeResolver = employeeResolver;
        this.branchRepository = branchRepository;
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(UUID employeeIdFilter, UUID branchIdFilter,
                                       LocalDate desde, LocalDate hasta,
                                       StafflyUserPrincipal principal) {
        List<Schedule> all = scheduleRepository.findByCompanyId(principal.getCompanyId());

        UUID effectiveEmpId = employeeIdFilter;
        if (principal.getRol() == Rol.EMPLOYEE) {
            effectiveEmpId = resolveOwnEmployeeId(principal);
        }
        final UUID finalEmpId = effectiveEmpId;

        return all.stream()
                .filter(s -> finalEmpId == null || s.getEmployee().getId().equals(finalEmpId))
                .filter(s -> branchIdFilter == null || s.getBranch().getId().equals(branchIdFilter))
                .filter(s -> desde == null || !s.getFechaHoraInicio().toLocalDate().isBefore(desde))
                .filter(s -> hasta == null || !s.getFechaHoraInicio().toLocalDate().isAfter(hasta))
                .filter(s -> principal.getRol() != Rol.SUPERVISOR
                        || principal.getBranchIds().contains(s.getBranch().getId()))
                .sorted(Comparator.comparing(Schedule::getFechaHoraInicio))
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request, StafflyUserPrincipal principal) {
        if (!request.fechaHoraFin().isAfter(request.fechaHoraInicio())) {
            throw new BadRequestException("fechaHoraFin debe ser posterior a fechaHoraInicio");
        }
        Employee employee = employeeResolver.resolveForCaller(request.employeeId(), principal, false);
        Branch branch = branchRepository.findByIdAndCompanyId(request.branchId(), principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada"));

        if (scheduleRepository.existsOverlap(principal.getCompanyId(), employee.getId(),
                request.fechaHoraInicio(), request.fechaHoraFin(), null)) {
            throw new ConflictException("El empleado ya tiene un turno en ese horario");
        }

        Schedule schedule = new Schedule();
        schedule.setCompanyId(principal.getCompanyId());
        schedule.setEmployee(employee);
        schedule.setBranch(branch);
        schedule.setFechaHoraInicio(request.fechaHoraInicio());
        schedule.setFechaHoraFin(request.fechaHoraFin());
        schedule.setTipoTurno(request.tipoTurno());
        schedule.setEstado(EstadoTurno.PLANIFICADO);
        schedule = scheduleRepository.save(schedule);

        String warning = checkDisponibilidad(schedule, principal.getCompanyId());
        if (warning != null) {
            eventPublisher.publishEvent(new AuditableFieldChangedEvent(
                    principal.getCompanyId(), "Schedule", schedule.getId(), principal.getUserId(),
                    "asignacion_fuera_disponibilidad", null, warning));
        }
        return ScheduleResponse.from(schedule, warning);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse findById(UUID id, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmpId = resolveOwnEmployeeId(principal);
            if (!schedule.getEmployee().getId().equals(ownEmpId)) {
                throw new ResourceNotFoundException("No se encontró el turno solicitado");
            }
        }
        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }
        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public ScheduleResponse update(UUID id, UpdateScheduleRequest request, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }

        if (request.branchId() != null) {
            Branch branch = branchRepository.findByIdAndCompanyId(request.branchId(), principal.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada"));
            schedule.setBranch(branch);
        }

        var inicioFinal = request.fechaHoraInicio() != null ? request.fechaHoraInicio() : schedule.getFechaHoraInicio();
        var finFinal = request.fechaHoraFin() != null ? request.fechaHoraFin() : schedule.getFechaHoraFin();

        if (!finFinal.isAfter(inicioFinal)) {
            throw new BadRequestException("fechaHoraFin debe ser posterior a fechaHoraInicio");
        }
        if (scheduleRepository.existsOverlap(principal.getCompanyId(), schedule.getEmployee().getId(),
                inicioFinal, finFinal, schedule.getId())) {
            throw new ConflictException("El empleado ya tiene un turno en ese horario");
        }

        schedule.setFechaHoraInicio(inicioFinal);
        schedule.setFechaHoraFin(finFinal);
        if (request.tipoTurno() != null) schedule.setTipoTurno(request.tipoTurno());
        schedule = scheduleRepository.save(schedule);

        String warning = checkDisponibilidad(schedule, principal.getCompanyId());
        if (warning != null) {
            eventPublisher.publishEvent(new AuditableFieldChangedEvent(
                    principal.getCompanyId(), "Schedule", schedule.getId(), principal.getUserId(),
                    "asignacion_fuera_disponibilidad", null, warning));
        }
        return ScheduleResponse.from(schedule, warning);
    }

    @Transactional
    public void delete(UUID id, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }
        if (schedule.getEstado() != EstadoTurno.PLANIFICADO) {
            throw new ConflictException("Solo se pueden eliminar turnos en estado PLANIFICADO");
        }
        scheduleRepository.delete(schedule);
    }

    @Transactional
    public ScheduleResponse confirm(UUID id, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }
        if (schedule.getEstado() != EstadoTurno.PLANIFICADO) {
            throw new ConflictException("Solo se pueden confirmar turnos en estado PLANIFICADO");
        }
        schedule.setEstado(EstadoTurno.CONFIRMADO);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleResponse updateStatus(UUID id, UpdateStatusRequest request, StafflyUserPrincipal principal) {
        Schedule schedule = scheduleRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el turno solicitado"));

        if (principal.getRol() == Rol.SUPERVISOR
                && !principal.getBranchIds().contains(schedule.getBranch().getId())) {
            throw new ResourceNotFoundException("No se encontró el turno solicitado");
        }
        if (schedule.getEstado() != EstadoTurno.CONFIRMADO) {
            throw new ConflictException("El estado solo puede cambiarse desde CONFIRMADO");
        }
        EstadoTurno nuevo = request.estado();
        if (nuevo != EstadoTurno.CUMPLIDO && nuevo != EstadoTurno.AUSENTE) {
            throw new ConflictException("Estado inválido: solo se permite CUMPLIDO o AUSENTE");
        }
        schedule.setEstado(nuevo);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    // ── disponibilidad ────────────────────────────────────────────────────────

    /**
     * Verifica que el turno esté completamente cubierto por la disponibilidad
     * declarada del empleado. Para turnos que cruzan medianoche, divide en dos
     * segmentos (día de inicio y día de fin) y verifica cada uno por separado.
     * Retorna "OUT_OF_AVAILABILITY" si falta cobertura, null si está cubierto.
     */
    private String checkDisponibilidad(Schedule schedule, UUID companyId) {
        var inicio = schedule.getFechaHoraInicio();
        var fin = schedule.getFechaHoraFin();

        boolean mismodia = inicio.toLocalDate().equals(fin.toLocalDate())
                || fin.toLocalTime().equals(LocalTime.MIDNIGHT);

        if (mismodia) {
            LocalTime tFin = fin.toLocalTime().equals(LocalTime.MIDNIGHT) ? LocalTime.MIDNIGHT : fin.toLocalTime();
            if (!isCovered(schedule.getEmployee().getId(), companyId,
                    inicio.getDayOfWeek(), inicio.toLocalTime(), tFin)) {
                return "OUT_OF_AVAILABILITY";
            }
        } else {
            // Segmento A: día de inicio, desde hora inicio hasta medianoche (1440 min)
            if (!isCovered(schedule.getEmployee().getId(), companyId,
                    inicio.getDayOfWeek(), inicio.toLocalTime(), LocalTime.MIDNIGHT)) {
                return "OUT_OF_AVAILABILITY";
            }
            // Segmento B: día de fin, desde medianoche hasta hora fin
            if (!isCovered(schedule.getEmployee().getId(), companyId,
                    fin.getDayOfWeek(), LocalTime.MIDNIGHT, fin.toLocalTime())) {
                return "OUT_OF_AVAILABILITY";
            }
        }
        return null;
    }

    /**
     * Retorna true si existe al menos una franja de disponibilidad del empleado
     * en el día indicado que contenga completamente el segmento [segInicio, segFin).
     * LocalTime.MIDNIGHT como segFin representa "fin de día" (1440 minutos).
     * LocalTime.MIDNIGHT como segInicio representa "inicio de día" (0 minutos).
     */
    private boolean isCovered(UUID employeeId, UUID companyId,
                               java.time.DayOfWeek dow, LocalTime segInicio, LocalTime segFin) {
        DiaSemana dia = DiaSemana.fromDayOfWeek(dow);
        List<EmployeeAvailability> franjas = availabilityRepository
                .findByCompanyIdAndEmployeeIdAndDiaSemana(companyId, employeeId, dia);
        if (franjas.isEmpty()) return false;

        int segInicioMin = toMinutes(segInicio);
        // LocalTime.MIDNIGHT como fin = 1440 (fin de día completo)
        int segFinMin = segFin.equals(LocalTime.MIDNIGHT) && !segInicio.equals(LocalTime.MIDNIGHT)
                ? 24 * 60
                : toMinutes(segFin);

        for (EmployeeAvailability franja : franjas) {
            int franjaInicioMin = toMinutes(franja.getHoraInicio());
            int franjaFinMin = franjaFinEnMinutos(franja.getHoraInicio(), franja.getHoraFin());
            if (franjaInicioMin <= segInicioMin && franjaFinMin >= segFinMin) {
                return true;
            }
        }
        return false;
    }

    private int toMinutes(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private int franjaFinEnMinutos(LocalTime inicio, LocalTime fin) {
        int min = toMinutes(fin);
        return min <= toMinutes(inicio) ? min + 24 * 60 : min;
    }

    private UUID resolveOwnEmployeeId(StafflyUserPrincipal principal) {
        return userRepository.findByIdAndCompanyId(principal.getUserId(), principal.getCompanyId())
                .map(User::getEmployee)
                .map(Employee::getId)
                .orElse(null);
    }
}
