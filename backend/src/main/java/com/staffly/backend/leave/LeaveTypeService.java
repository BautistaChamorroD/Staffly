package com.staffly.backend.leave;

import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.leave.dto.CreateLeaveTypeRequest;
import com.staffly.backend.leave.dto.LeaveTypeResponse;
import com.staffly.backend.leave.dto.UpdateLeaveTypeRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> list(StafflyUserPrincipal principal) {
        return leaveTypeRepository.findByCompanyId(principal.getCompanyId())
                .stream()
                .sorted(Comparator.comparing(LeaveType::getNombre))
                .map(LeaveTypeResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeaveTypeResponse create(CreateLeaveTypeRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        String nombre = request.nombre().strip();

        validarNombreUnico(companyId, nombre, null);

        LeaveType leaveType = new LeaveType();
        leaveType.setCompanyId(companyId);
        leaveType.setNombre(nombre);
        leaveType.setEsPaga(request.esPaga());
        leaveType.setCupoAnual(request.cupoAnual());
        return LeaveTypeResponse.from(leaveTypeRepository.save(leaveType));
    }

    @Transactional
    public LeaveTypeResponse update(UUID id, UpdateLeaveTypeRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        LeaveType leaveType = leaveTypeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el tipo de licencia solicitado"));

        String nombreFinal = request.nombre() != null ? request.nombre().strip() : leaveType.getNombre();
        boolean esPagaFinal = request.esPaga() != null ? request.esPaga() : leaveType.isEsPaga();
        Integer cupoAnualFinal = request.cupoAnual() != null ? request.cupoAnual() : leaveType.getCupoAnual();

        if (nombreFinal.isBlank()) {
            throw new BadRequestException("El nombre no puede estar vacío");
        }

        validarNombreUnico(companyId, nombreFinal, leaveType.getId());

        leaveType.setNombre(nombreFinal);
        leaveType.setEsPaga(esPagaFinal);
        leaveType.setCupoAnual(cupoAnualFinal);
        return LeaveTypeResponse.from(leaveTypeRepository.save(leaveType));
    }

    @Transactional
    public void delete(UUID id, StafflyUserPrincipal principal) {
        LeaveType leaveType = leaveTypeRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el tipo de licencia solicitado"));
        leaveTypeRepository.delete(leaveType);
    }

    private void validarNombreUnico(UUID companyId, String nombre, UUID excludeId) {
        boolean existe = excludeId == null
                ? leaveTypeRepository.existsByCompanyIdAndNombre(companyId, nombre)
                : leaveTypeRepository.existsByCompanyIdAndNombreAndIdNot(companyId, nombre, excludeId);
        if (existe) {
            throw new ConflictException("Ya existe un tipo de licencia con ese nombre en la empresa");
        }
    }
}
