package com.staffly.backend.user;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.common.TemporaryPasswordGenerator;
import com.staffly.backend.employee.Employee;
import com.staffly.backend.employee.EmployeeRepository;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import com.staffly.backend.user.dto.CreateUserRequest;
import com.staffly.backend.user.dto.CreateUserResponse;
import com.staffly.backend.user.dto.UpdateUserRequest;
import com.staffly.backend.user.dto.UpdateUserStatusRequest;
import com.staffly.backend.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;

    public UserService(
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            BranchRepository branchRepository,
            PasswordEncoder passwordEncoder,
            TemporaryPasswordGenerator temporaryPasswordGenerator) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(RolUsuario rolFilter, EstadoUsuario estadoFilter) {
        return userRepository.findAll().stream()
                .filter(u -> rolFilter == null || u.getRol() == rolFilter)
                .filter(u -> estadoFilter == null || u.getEstado() == estadoFilter)
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id, StafflyUserPrincipal principal) {
        if (principal.getRol() != Rol.ADMIN && !id.equals(principal.getUserId())) {
            throw new ResourceNotFoundException("No se encontró el usuario solicitado");
        }
        return UserResponse.from(findUserOrThrow(id, principal.getCompanyId()));
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(StafflyUserPrincipal principal) {
        return UserResponse.from(findUserOrThrow(principal.getUserId(), principal.getCompanyId()));
    }

    @Transactional
    public CreateUserResponse create(CreateUserRequest request, StafflyUserPrincipal principal) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ConflictException("El email ya está en uso");
        }

        User user = new User();
        user.setCompanyId(principal.getCompanyId());
        user.setEmail(request.email());
        user.setRol(request.rol());
        user.setEstado(EstadoUsuario.ACTIVO);
        user.setDebeCambiarPassword(true);

        if (request.employeeId() != null) {
            user.setEmployee(resolveEmployee(request.employeeId(), principal.getCompanyId()));
        }
        if (request.rol() == RolUsuario.SUPERVISOR && request.branchIds() != null) {
            user.getBranches().addAll(resolveBranches(request.branchIds(), principal.getCompanyId()));
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));

        user = userRepository.save(user);
        return new CreateUserResponse(UserResponse.from(user), temporaryPassword);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, StafflyUserPrincipal principal) {
        User user = findUserOrThrow(id, principal.getCompanyId());

        if (request.rol() != null) {
            user.setRol(request.rol());
            if (request.rol() != RolUsuario.SUPERVISOR) {
                user.getBranches().clear();
            }
        }
        if (request.branchIds() != null && user.getRol() == RolUsuario.SUPERVISOR) {
            user.getBranches().clear();
            user.getBranches().addAll(resolveBranches(request.branchIds(), principal.getCompanyId()));
        }

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UpdateUserStatusRequest request, StafflyUserPrincipal principal) {
        User user = findUserOrThrow(id, principal.getCompanyId());
        user.setEstado(request.estado());
        return UserResponse.from(userRepository.save(user));
    }

    private Employee resolveEmployee(UUID employeeId, UUID companyId) {
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el empleado solicitado"));
    }

    private Set<Branch> resolveBranches(List<UUID> branchIds, UUID companyId) {
        Set<Branch> branches = new HashSet<>();
        for (UUID branchId : branchIds) {
            branches.add(branchRepository.findByIdAndCompanyId(branchId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada: " + branchId)));
        }
        return branches;
    }

    private User findUserOrThrow(UUID id, UUID companyId) {
        return userRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario solicitado"));
    }
}
