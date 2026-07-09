package com.staffly.backend.employee;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.employee.dto.CreateEmployeeRequest;
import com.staffly.backend.employee.dto.EmployeeResponse;
import com.staffly.backend.employee.dto.UpdateEmployeeRequest;
import com.staffly.backend.employee.dto.UpdateEmployeeStatusRequest;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    public EmployeeService(EmployeeRepository employeeRepository, BranchRepository branchRepository, UserRepository userRepository) {
        this.employeeRepository = employeeRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(EstadoLaboral estadoLaboral, UUID branchId, String search, StafflyUserPrincipal principal) {
        String normalizedSearch = search != null ? search.toLowerCase() : null;
        return employeeRepository.findAll().stream()
                .filter(e -> isInScope(e, principal))
                .filter(e -> estadoLaboral == null || e.getEstadoLaboral() == estadoLaboral)
                .filter(e -> branchId == null || e.getBranches().stream().anyMatch(b -> b.getId().equals(branchId)))
                .filter(e -> normalizedSearch == null
                        || e.getNombre().toLowerCase().contains(normalizedSearch)
                        || e.getApellido().toLowerCase().contains(normalizedSearch)
                        || e.getDocumento().toLowerCase().contains(normalizedSearch))
                .map(EmployeeResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(UUID id, StafflyUserPrincipal principal) {
        return EmployeeResponse.from(findEmployeeOrThrow(id, principal));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getMe(StafflyUserPrincipal principal) {
        User user = userRepository.findByIdAndCompanyId(principal.getUserId(), principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario autenticado"));
        Employee employee = user.getEmployee();
        if (employee == null) {
            throw new ResourceNotFoundException("El usuario autenticado no tiene un empleado vinculado");
        }
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request, StafflyUserPrincipal principal) {
        Employee employee = new Employee();
        employee.setCompanyId(principal.getCompanyId());
        employee.setNombre(request.nombre());
        employee.setApellido(request.apellido());
        employee.setDocumento(request.documento());
        employee.setFechaNacimiento(request.fechaNacimiento());
        employee.setFechaIngreso(request.fechaIngreso());
        employee.setFechaEgreso(request.fechaEgreso());
        employee.setTipoContrato(request.tipoContrato());
        employee.setCategoria(request.categoria());
        employee.setSueldoBase(request.sueldoBase());
        employee.setTelefono(request.telefono());
        employee.setEmailContacto(request.emailContacto());
        employee.setEstadoLaboral(EstadoLaboral.ACTIVO);
        employee.setEstadoLiquidacion(EstadoLiquidacion.AL_DIA);
        employee.getBranches().addAll(resolveBranches(request.branchIds(), principal.getCompanyId()));

        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(UUID id, UpdateEmployeeRequest request, StafflyUserPrincipal principal) {
        Employee employee = findEmployeeOrThrow(id, principal);

        if (request.nombre() != null) {
            employee.setNombre(request.nombre());
        }
        if (request.apellido() != null) {
            employee.setApellido(request.apellido());
        }
        if (request.documento() != null) {
            employee.setDocumento(request.documento());
        }
        if (request.fechaNacimiento() != null) {
            employee.setFechaNacimiento(request.fechaNacimiento());
        }
        if (request.fechaIngreso() != null) {
            employee.setFechaIngreso(request.fechaIngreso());
        }
        if (request.fechaEgreso() != null) {
            employee.setFechaEgreso(request.fechaEgreso());
        }
        if (request.tipoContrato() != null) {
            employee.setTipoContrato(request.tipoContrato());
        }
        if (request.categoria() != null) {
            employee.setCategoria(request.categoria());
        }
        if (request.sueldoBase() != null) {
            employee.setSueldoBase(request.sueldoBase());
        }
        if (request.telefono() != null) {
            employee.setTelefono(request.telefono());
        }
        if (request.emailContacto() != null) {
            employee.setEmailContacto(request.emailContacto());
        }
        if (request.branchIds() != null) {
            employee.getBranches().clear();
            employee.getBranches().addAll(resolveBranches(request.branchIds(), principal.getCompanyId()));
        }

        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse updateStatus(UUID id, UpdateEmployeeStatusRequest request, StafflyUserPrincipal principal) {
        Employee employee = findEmployeeOrThrow(id, principal);
        employee.setEstadoLaboral(request.estadoLaboral());
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public List<Object> getHistory(UUID id, StafflyUserPrincipal principal) {
        findEmployeeOrThrow(id, principal);
        // AuditLog todavía no existe (BE-1.8, opcional). El contrato del
        // endpoint queda estable desde ya; cuando exista, esto pasa a
        // consultar el historial real en vez de devolver vacío.
        return List.of();
    }

    private Set<Branch> resolveBranches(List<UUID> branchIds, UUID companyId) {
        Set<Branch> branches = new HashSet<>();
        for (UUID branchId : branchIds) {
            branches.add(branchRepository.findByIdAndCompanyId(branchId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada: " + branchId)));
        }
        return branches;
    }

    private Employee findEmployeeOrThrow(UUID id, StafflyUserPrincipal principal) {
        Employee employee = employeeRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado solicitado"));
        if (!isInScope(employee, principal)) {
            throw new ResourceNotFoundException("No se encontró el empleado solicitado");
        }
        return employee;
    }

    /**
     * ADMIN/RRHH ven cualquier empleado de la empresa. SUPERVISOR solo los
     * que tienen alguna sucursal en principal.getBranchIds() (viene del JWT).
     */
    private boolean isInScope(Employee employee, StafflyUserPrincipal principal) {
        if (principal.getRol() != Rol.SUPERVISOR) {
            return true;
        }
        return employee.getBranches().stream().anyMatch(b -> principal.getBranchIds().contains(b.getId()));
    }
}
