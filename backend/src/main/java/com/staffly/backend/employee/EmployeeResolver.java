package com.staffly.backend.employee;

import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Componente compartido que resuelve el Employee que el caller puede tocar,
 * aplicando las tres capas de scoping: tenant (404), rol EMPLOYEE (403/self-only),
 * SUPERVISOR (404 fuera de sus sucursales). Evita duplicación entre
 * AvailabilityService, ScheduleService, y futuros servicios.
 */
@Component
public class EmployeeResolver {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    public EmployeeResolver(EmployeeRepository employeeRepository, UserRepository userRepository) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    /**
     * @param allowEmployee true = EMPLOYEE puede acceder a su propio registro (403 si intenta ver otro).
     *                      false = EMPLOYEE no tiene acceso (403 directo).
     */
    public Employee resolveForCaller(UUID employeeId, StafflyUserPrincipal principal, boolean allowEmployee) {
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado solicitado"));

        if (principal.getRol() == Rol.EMPLOYEE) {
            if (!allowEmployee) {
                throw new AccessDeniedException("No tenés permisos para esta operación");
            }
            UUID ownEmployeeId = userRepository.findByIdAndCompanyId(principal.getUserId(), principal.getCompanyId())
                    .map(User::getEmployee)
                    .map(Employee::getId)
                    .orElse(null);
            if (!employee.getId().equals(ownEmployeeId)) {
                throw new AccessDeniedException("Solo podés acceder a tu propio registro");
            }
        }

        if (principal.getRol() == Rol.SUPERVISOR
                && employee.getBranches().stream().noneMatch(b -> principal.getBranchIds().contains(b.getId()))) {
            throw new ResourceNotFoundException("No se encontró el empleado solicitado");
        }

        return employee;
    }
}
