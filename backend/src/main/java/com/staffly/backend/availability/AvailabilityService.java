// backend/src/main/java/com/staffly/backend/availability/AvailabilityService.java
package com.staffly.backend.availability;

import com.staffly.backend.availability.dto.AvailabilityResponse;
import com.staffly.backend.availability.dto.CreateAvailabilityRequest;
import com.staffly.backend.availability.dto.UpdateAvailabilityRequest;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeResolver;
import com.staffly.backend.security.StafflyUserPrincipal;
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
    private final EmployeeResolver employeeResolver;

    public AvailabilityService(AvailabilityRepository availabilityRepository, EmployeeResolver employeeResolver) {
        this.availabilityRepository = availabilityRepository;
        this.employeeResolver = employeeResolver;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> list(UUID employeeId, StafflyUserPrincipal principal) {
        Employee employee = employeeResolver.resolveForCaller(employeeId, principal, true);
        return availabilityRepository.findByCompanyIdAndEmployeeId(principal.getCompanyId(), employee.getId()).stream()
                .sorted(Comparator
                        .comparing((EmployeeAvailability a) -> a.getDiaSemana().ordinal())
                        .thenComparing(EmployeeAvailability::getHoraInicio))
                .map(AvailabilityResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AvailabilityResponse create(UUID employeeId, CreateAvailabilityRequest request, StafflyUserPrincipal principal) {
        Employee employee = employeeResolver.resolveForCaller(employeeId, principal, true);
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
        Employee employee = employeeResolver.resolveForCaller(employeeId, principal, true);
        EmployeeAvailability availability = findFranjaOrThrow(id, employee, principal);

        DiaSemana diaFinal = request.diaSemana() != null ? request.diaSemana() : availability.getDiaSemana();
        LocalTime inicioFinal = request.horaInicio() != null ? request.horaInicio() : availability.getHoraInicio();
        LocalTime finFinal = request.horaFin() != null ? request.horaFin() : availability.getHoraFin();

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
        Employee employee = employeeResolver.resolveForCaller(employeeId, principal, true);
        EmployeeAvailability availability = findFranjaOrThrow(id, employee, principal);
        availabilityRepository.delete(availability);
    }

    private EmployeeAvailability findFranjaOrThrow(UUID id, Employee employee, StafflyUserPrincipal principal) {
        EmployeeAvailability availability = availabilityRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la franja solicitada"));
        if (!availability.getEmployee().getId().equals(employee.getId())) {
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
            if (existente.getId().equals(excludeId)) continue;
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

    private int finEnMinutos(LocalTime inicio, LocalTime fin) {
        int minutosFin = enMinutos(fin);
        return minutosFin <= enMinutos(inicio) ? minutosFin + 24 * 60 : minutosFin;
    }
}
