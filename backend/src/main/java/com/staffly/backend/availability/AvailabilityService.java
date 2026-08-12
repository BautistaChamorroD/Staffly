package com.staffly.backend.availability;

import com.staffly.backend.availability.dto.AvailabilityResponse;
import com.staffly.backend.availability.dto.CreateAvailabilityRequest;
import com.staffly.backend.availability.dto.UpdateAvailabilityRequest;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public AvailabilityService(
            AvailabilityRepository availabilityRepository,
            EmployeeRepository employeeRepository,
            UserRepository userRepository) {
        this.availabilityRepository = availabilityRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> list(UUID employeeId, StafflyUserPrincipal principal) {
        Employee employee = resolveEmployee(employeeId, principal);
        return availabilityRepository.findByCompanyIdAndEmployeeId(principal.getCompanyId(), employee.getId()).stream()
                // orden semanal (LUNES primero) por ordinal del enum: se
                // persiste como STRING, un ORDER BY en SQL sería alfabético
                .sorted(Comparator
                        .comparing((EmployeeAvailability a) -> a.getDiaSemana().ordinal())
                        .thenComparing(EmployeeAvailability::getHoraInicio))
                .map(AvailabilityResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AvailabilityResponse create(UUID employeeId, CreateAvailabilityRequest request, StafflyUserPrincipal principal) {
        Employee employee = resolveEmployee(employeeId, principal);
        // se trunca a minuto en la entrada: enMinutos/finEnMinutos operan a
        // granularidad de minuto y no deben recibir segundos no nulos
        LocalTime horaInicio = request.horaInicio().truncatedTo(ChronoUnit.MINUTES);
        LocalTime horaFin = request.horaFin().truncatedTo(ChronoUnit.MINUTES);
        validarFranja(horaInicio, horaFin);
        validarSolape(employee, request.diaSemana(), horaInicio, horaFin, null, principal);

        EmployeeAvailability availability = new EmployeeAvailability();
        availability.setCompanyId(principal.getCompanyId());
        availability.setEmployee(employee);
        availability.setDiaSemana(request.diaSemana());
        availability.setHoraInicio(horaInicio);
        availability.setHoraFin(horaFin);
        return AvailabilityResponse.from(availabilityRepository.save(availability));
    }

    @Transactional
    public AvailabilityResponse update(
            UUID employeeId, UUID id, UpdateAvailabilityRequest request, StafflyUserPrincipal principal) {
        Employee employee = resolveEmployee(employeeId, principal);
        EmployeeAvailability availability = findFranjaOrThrow(id, employee, principal);

        // estado final = valor entrante o el guardado, según qué llegue
        DiaSemana diaFinal = request.diaSemana() != null ? request.diaSemana() : availability.getDiaSemana();
        LocalTime inicioFinal = request.horaInicio() != null ? request.horaInicio() : availability.getHoraInicio();
        LocalTime finFinal = request.horaFin() != null ? request.horaFin() : availability.getHoraFin();

        // se trunca a minuto tanto el valor entrante como el ya persistido
        // reutilizado, para que ambos queden a la misma granularidad que
        // enMinutos/finEnMinutos asumen
        inicioFinal = inicioFinal.truncatedTo(ChronoUnit.MINUTES);
        finFinal = finFinal.truncatedTo(ChronoUnit.MINUTES);

        validarFranja(inicioFinal, finFinal);
        validarSolape(employee, diaFinal, inicioFinal, finFinal, availability.getId(), principal);

        availability.setDiaSemana(diaFinal);
        availability.setHoraInicio(inicioFinal);
        availability.setHoraFin(finFinal);
        return AvailabilityResponse.from(availabilityRepository.save(availability));
    }

    @Transactional
    public void delete(UUID employeeId, UUID id, StafflyUserPrincipal principal) {
        Employee employee = resolveEmployee(employeeId, principal);
        EmployeeAvailability availability = findFranjaOrThrow(id, employee, principal);
        availabilityRepository.delete(availability);
    }

    /**
     * Resuelve el empleado del path aplicando las tres capas de scoping:
     * tenant (otra empresa → 404), EMPLOYEE solo su propio registro (otro
     * empleado → 403, nota RF-29 de api-design), SUPERVISOR solo empleados
     * de sus sucursales asignadas (fuera de alcance → 404, mismo criterio
     * que EmployeeService).
     */
    private Employee resolveEmployee(UUID employeeId, StafflyUserPrincipal principal) {
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado solicitado"));

        if (principal.getRol() == Rol.EMPLOYEE) {
            UUID ownEmployeeId = userRepository.findByIdAndCompanyId(principal.getUserId(), principal.getCompanyId())
                    .map(User::getEmployee)
                    .map(Employee::getId)
                    .orElse(null);
            if (!employee.getId().equals(ownEmployeeId)) {
                throw new AccessDeniedException("Solo podés acceder a tu propia disponibilidad");
            }
        }

        if (principal.getRol() == Rol.SUPERVISOR
                && employee.getBranches().stream().noneMatch(b -> principal.getBranchIds().contains(b.getId()))) {
            throw new ResourceNotFoundException("No se encontró el empleado solicitado");
        }

        return employee;
    }

    private EmployeeAvailability findFranjaOrThrow(UUID id, Employee employee, StafflyUserPrincipal principal) {
        EmployeeAvailability availability = availabilityRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la franja solicitada"));
        if (!availability.getEmployee().getId().equals(employee.getId())) {
            // la franja existe pero pertenece a otro empleado: bajo este
            // employeeId no se encuentra
            throw new ResourceNotFoundException("No se encontró la franja solicitada");
        }
        return availability;
    }

    private void validarFranja(LocalTime horaInicio, LocalTime horaFin) {
        if (horaInicio.equals(horaFin)) {
            throw new BadRequestException("La franja no puede empezar y terminar a la misma hora");
        }
    }

    private void validarSolape(
            Employee employee, DiaSemana dia, LocalTime inicio, LocalTime fin, UUID excludeId,
            StafflyUserPrincipal principal) {
        int nuevoInicio = enMinutos(inicio);
        int nuevoFin = finEnMinutos(inicio, fin);
        for (EmployeeAvailability existente : availabilityRepository
                .findByCompanyIdAndEmployeeIdAndDiaSemana(principal.getCompanyId(), employee.getId(), dia)) {
            if (existente.getId().equals(excludeId)) {
                continue;
            }
            int inicioExistente = enMinutos(existente.getHoraInicio());
            int finExistente = finEnMinutos(existente.getHoraInicio(), existente.getHoraFin());
            if (nuevoInicio < finExistente && inicioExistente < nuevoFin) {
                throw new ConflictException("La franja se solapa con otra ya cargada para ese día");
            }
        }
    }

    private int enMinutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    /**
     * Fin del intervalo [inicio, fin) en minutos, con wrap: si la hora de
     * fin es menor o igual a la de inicio, la franja cruza medianoche y el
     * fin se corre +24h (viernes 20:00–02:00 → [1200, 1560)).
     */
    private int finEnMinutos(LocalTime inicio, LocalTime fin) {
        int minutosFin = enMinutos(fin);
        return minutosFin <= enMinutos(inicio) ? minutosFin + 24 * 60 : minutosFin;
    }
}
