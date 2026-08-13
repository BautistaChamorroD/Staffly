// backend/src/main/java/com/staffly/backend/holiday/HolidayService.java
package com.staffly.backend.holiday;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.branch.BranchRepository;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.holiday.dto.CreateHolidayRequest;
import com.staffly.backend.holiday.dto.HolidayResponse;
import com.staffly.backend.holiday.dto.UpdateHolidayRequest;
import com.staffly.backend.security.Rol;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final BranchRepository branchRepository;

    public HolidayService(HolidayRepository holidayRepository, BranchRepository branchRepository) {
        this.holidayRepository = holidayRepository;
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> list(UUID branchId, Integer anio, StafflyUserPrincipal principal) {
        // SUPERVISOR: ?branchId= de una sucursal fuera de su alcance → 404
        if (principal.getRol() == Rol.SUPERVISOR
                && branchId != null
                && !principal.getBranchIds().contains(branchId)) {
            throw new ResourceNotFoundException("No se encontró la sucursal solicitada");
        }

        UUID companyId = principal.getCompanyId();
        List<Holiday> holidays;
        if (anio != null) {
            holidays = holidayRepository.findByCompanyIdAndFechaBetween(
                    companyId, LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31));
        } else {
            holidays = holidayRepository.findByCompanyId(companyId);
        }

        return holidays.stream()
                .filter(h -> isVisibleFor(h, branchId, principal))
                .sorted(Comparator.comparing(Holiday::getFecha))
                .map(HolidayResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public HolidayResponse create(CreateHolidayRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        Branch branch = resolveBranch(request.branchId(), companyId);
        validarDuplicado(companyId, request.branchId(), request.fecha(), null);

        Holiday holiday = new Holiday();
        holiday.setCompanyId(companyId);
        holiday.setBranch(branch);
        holiday.setFecha(request.fecha());
        holiday.setNombre(request.nombre().strip());
        holiday.setRecurrente(request.recurrente());
        return HolidayResponse.from(holidayRepository.save(holiday));
    }

    @Transactional
    public HolidayResponse update(UUID id, UpdateHolidayRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el feriado solicitado"));

        // estado final: valor entrante o el ya guardado
        UUID branchIdFinal = request.branchId() != null ? request.branchId() : holiday.getBranchId();
        LocalDate fechaFinal = request.fecha() != null ? request.fecha() : holiday.getFecha();
        String nombreFinal = request.nombre() != null ? request.nombre().strip() : holiday.getNombre();
        boolean recurrenteFinal = request.recurrente() != null ? request.recurrente() : holiday.isRecurrente();

        if (nombreFinal.isBlank()) {
            throw new BadRequestException("El nombre no puede estar vacío");
        }

        Branch branch = resolveBranch(branchIdFinal, companyId);
        validarDuplicado(companyId, branchIdFinal, fechaFinal, holiday.getId());

        holiday.setBranch(branch);
        holiday.setFecha(fechaFinal);
        holiday.setNombre(nombreFinal);
        holiday.setRecurrente(recurrenteFinal);
        return HolidayResponse.from(holidayRepository.save(holiday));
    }

    @Transactional
    public void delete(UUID id, StafflyUserPrincipal principal) {
        Holiday holiday = holidayRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el feriado solicitado"));
        holidayRepository.delete(holiday);
    }

    // ── helpers privados ──────────────────────────────────────────────────────

    /**
     * Devuelve el Branch si branchId no es null; verifica que pertenezca a la
     * empresa del JWT. branchId null = feriado global → devuelve null.
     */
    private Branch resolveBranch(UUID branchId, UUID companyId) {
        if (branchId == null) return null;
        return branchRepository.findByIdAndCompanyId(branchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la sucursal solicitada"));
    }

    /**
     * Verifica que no exista otro feriado con la misma (company_id, branch_id,
     * fecha). excludeId se usa en PATCH para que el propio feriado no se cuente
     * como duplicado de sí mismo. Las dos ramas del if son necesarias porque
     * Spring Data JPA no genera IS NULL cuando se pasa null directamente a un
     * parámetro de query (necesita método distinto con IsNull en el nombre).
     */
    private void validarDuplicado(UUID companyId, UUID branchId, LocalDate fecha, UUID excludeId) {
        boolean existe;
        if (branchId == null) {
            existe = excludeId == null
                    ? holidayRepository.existsByCompanyIdAndBranchIdIsNullAndFecha(companyId, fecha)
                    : holidayRepository.existsByCompanyIdAndBranchIdIsNullAndFechaAndIdNot(companyId, fecha, excludeId);
        } else {
            existe = excludeId == null
                    ? holidayRepository.existsByCompanyIdAndBranchIdAndFecha(companyId, branchId, fecha)
                    : holidayRepository.existsByCompanyIdAndBranchIdAndFechaAndIdNot(companyId, branchId, fecha, excludeId);
        }
        if (existe) {
            throw new ConflictException("Ya existe un feriado para esa fecha en ese ámbito");
        }
    }

    /**
     * Determina si un holiday es visible para el rol/filtro dados.
     * Reglas:
     * - ?branchId= X: solo globales + feriados de esa sucursal
     * - SUPERVISOR: solo globales + feriados de sus sucursales asignadas
     */
    private boolean isVisibleFor(Holiday holiday, UUID branchIdFilter, StafflyUserPrincipal principal) {
        UUID hBranchId = holiday.getBranchId();

        if (branchIdFilter != null && hBranchId != null && !hBranchId.equals(branchIdFilter)) {
            return false;
        }

        if (principal.getRol() == Rol.SUPERVISOR
                && hBranchId != null
                && !principal.getBranchIds().contains(hBranchId)) {
            return false;
        }

        return true;
    }
}
