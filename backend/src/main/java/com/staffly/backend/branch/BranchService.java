package com.staffly.backend.branch;

import com.staffly.backend.branch.dto.BranchResponse;
import com.staffly.backend.branch.dto.CreateBranchRequest;
import com.staffly.backend.branch.dto.UpdateBranchRequest;
import com.staffly.backend.branch.dto.UpdateBranchStatusRequest;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> list() {
        return branchRepository.findAll().stream()
                .map(BranchResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BranchResponse getById(UUID id, StafflyUserPrincipal principal) {
        return BranchResponse.from(findBranchOrThrow(id, principal.getCompanyId()));
    }

    @Transactional
    public BranchResponse create(CreateBranchRequest request, StafflyUserPrincipal principal) {
        Branch branch = new Branch();
        branch.setCompanyId(principal.getCompanyId());
        branch.setNombre(request.nombre());
        branch.setDireccion(request.direccion());
        branch.setZonaHoraria(request.zonaHoraria());
        branch.setHorarioVisibleInicio(request.horarioVisibleInicio());
        branch.setHorarioVisibleFin(request.horarioVisibleFin());
        branch.setEstado(EstadoSucursal.ACTIVA);
        return BranchResponse.from(branchRepository.save(branch));
    }

    @Transactional
    public BranchResponse update(UUID id, UpdateBranchRequest request, StafflyUserPrincipal principal) {
        Branch branch = findBranchOrThrow(id, principal.getCompanyId());

        if (request.nombre() != null) {
            branch.setNombre(request.nombre());
        }
        if (request.direccion() != null) {
            branch.setDireccion(request.direccion());
        }
        if (request.zonaHoraria() != null) {
            branch.setZonaHoraria(request.zonaHoraria());
        }
        if (request.horarioVisibleInicio() != null) {
            branch.setHorarioVisibleInicio(request.horarioVisibleInicio());
        }
        if (request.horarioVisibleFin() != null) {
            branch.setHorarioVisibleFin(request.horarioVisibleFin());
        }

        return BranchResponse.from(branchRepository.save(branch));
    }

    @Transactional
    public BranchResponse updateStatus(UUID id, UpdateBranchStatusRequest request, StafflyUserPrincipal principal) {
        Branch branch = findBranchOrThrow(id, principal.getCompanyId());
        branch.setEstado(request.estado());
        return BranchResponse.from(branchRepository.save(branch));
    }

    private Branch findBranchOrThrow(UUID id, UUID companyId) {
        return branchRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada"));
    }
}
