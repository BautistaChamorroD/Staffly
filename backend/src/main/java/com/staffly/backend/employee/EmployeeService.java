package com.staffly.backend.employee;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.common.audit.AuditLogRepository;
import com.staffly.backend.common.audit.AuditableFieldChangedEvent;
import com.staffly.backend.employee.dto.CreateEmployeeRequest;
import com.staffly.backend.employee.dto.EmployeeHistoryEntry;
import com.staffly.backend.employee.dto.EmployeeResponse;
import com.staffly.backend.employee.dto.UpdateEmployeeRequest;
import com.staffly.backend.employee.dto.UpdateEmployeeStatusRequest;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.User;
import com.staffly.backend.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    /** Discriminador de Employee en la tabla genérica audit_log. */
    static final String AUDIT_ENTITY_TYPE = "EMPLOYEE";

    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            BranchRepository branchRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            ApplicationEventPublisher eventPublisher) {
        this.employeeRepository = employeeRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(EstadoLaboral estadoLaboral, UUID branchId, String search, StafflyUserPrincipal principal) {
        if (principal.getRol() == Rol.SUPERVISOR && principal.getBranchIds().isEmpty()) {
            // sin sucursales asignadas no hay nada visible — y evita un
            // IN () vacío inválido en SQL
            return List.of();
        }
        return employeeRepository.findAll(listSpecification(estadoLaboral, branchId, search, principal)).stream()
                .map(EmployeeResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Todos los filtros del listado resueltos en SQL (antes: findAll() +
     * filtrado en memoria, que cargaba la tabla entera por request). El
     * tenantFilter de Hibernate también aplica; el predicado por companyId
     * queda además explícito como doble defensa, igual que en los lookups.
     */
    private Specification<Employee> listSpecification(
            EstadoLaboral estadoLaboral, UUID branchId, String search, StafflyUserPrincipal principal) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), principal.getCompanyId()));
            if (estadoLaboral != null) {
                predicates.add(cb.equal(root.get("estadoLaboral"), estadoLaboral));
            }
            if (branchId != null) {
                predicates.add(cb.equal(root.join("branches").get("id"), branchId));
                query.distinct(true);
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), pattern),
                        cb.like(cb.lower(root.get("apellido")), pattern),
                        cb.like(cb.lower(root.get("documento")), pattern)));
            }
            if (principal.getRol() == Rol.SUPERVISOR) {
                predicates.add(root.join("branches").get("id").in(principal.getBranchIds()));
                query.distinct(true);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
        if (employeeRepository.existsByCompanyIdAndDocumento(principal.getCompanyId(), request.documento())) {
            throw new ConflictException("Ya existe un empleado con ese documento");
        }
        validarFechas(request.fechaIngreso(), request.fechaEgreso());

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

        if (request.documento() != null
                && !request.documento().equals(employee.getDocumento())
                && employeeRepository.existsByCompanyIdAndDocumentoAndIdNot(
                        principal.getCompanyId(), request.documento(), employee.getId())) {
            throw new ConflictException("Ya existe un empleado con ese documento");
        }
        // valida contra el valor entrante o el guardado, según qué llegue:
        // un PATCH con solo fechaEgreso también debe respetar la fechaIngreso
        // ya persistida
        LocalDate fechaIngresoFinal = request.fechaIngreso() != null ? request.fechaIngreso() : employee.getFechaIngreso();
        LocalDate fechaEgresoFinal = request.fechaEgreso() != null ? request.fechaEgreso() : employee.getFechaEgreso();
        validarFechas(fechaIngresoFinal, fechaEgresoFinal);

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
        // categoria y sueldoBase son los campos con historial (RF-06):
        // solo se registran cuando el valor realmente cambia
        if (request.categoria() != null && !request.categoria().equals(employee.getCategoria())) {
            publishFieldChange(employee, principal, "categoria", employee.getCategoria(), request.categoria());
            employee.setCategoria(request.categoria());
        }
        if (request.sueldoBase() != null && request.sueldoBase().compareTo(employee.getSueldoBase()) != 0) {
            publishFieldChange(
                    employee, principal, "sueldoBase",
                    formatMonto(employee.getSueldoBase()), formatMonto(request.sueldoBase()));
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
        if (request.estadoLaboral() != employee.getEstadoLaboral()) {
            publishFieldChange(
                    employee, principal, "estadoLaboral",
                    employee.getEstadoLaboral().name(), request.estadoLaboral().name());
            employee.setEstadoLaboral(request.estadoLaboral());
        }
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public List<EmployeeHistoryEntry> getHistory(UUID id, StafflyUserPrincipal principal) {
        findEmployeeOrThrow(id, principal);
        return auditLogRepository
                .findByCompanyIdAndEntityTypeAndEntityIdOrderByFechaDesc(
                        principal.getCompanyId(), AUDIT_ENTITY_TYPE, id)
                .stream()
                .map(EmployeeHistoryEntry::from)
                .collect(Collectors.toList());
    }

    private void publishFieldChange(
            Employee employee, StafflyUserPrincipal principal, String campo, String valorAnterior, String valorNuevo) {
        eventPublisher.publishEvent(new AuditableFieldChangedEvent(
                principal.getCompanyId(), AUDIT_ENTITY_TYPE, employee.getId(),
                principal.getUserId(), campo, valorAnterior, valorNuevo));
    }

    /** Sin ceros de más ni notación científica: 500000.00 → "500000". */
    private String formatMonto(BigDecimal monto) {
        return monto.stripTrailingZeros().toPlainString();
    }

    private void validarFechas(LocalDate fechaIngreso, LocalDate fechaEgreso) {
        if (fechaEgreso != null && fechaEgreso.isBefore(fechaIngreso)) {
            throw new BadRequestException("La fecha de egreso no puede ser anterior a la fecha de ingreso");
        }
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
