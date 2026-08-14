package com.staffly.backend.payroll;

import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.payroll.dto.CreatePayrollPeriodRequest;
import com.staffly.backend.payroll.dto.PayrollPeriodResponse;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PayrollPeriodService {

    private final PayrollPeriodRepository repository;

    public PayrollPeriodService(PayrollPeriodRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PayrollPeriodResponse> list(EstadoPeriodo estado, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        List<PayrollPeriod> periods = estado != null
                ? repository.findByCompanyIdAndEstadoOrderByFechaInicioDesc(companyId, estado)
                : repository.findByCompanyIdOrderByFechaInicioDesc(companyId);
        return periods.stream().map(PayrollPeriodResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PayrollPeriodResponse getById(UUID id, StafflyUserPrincipal principal) {
        PayrollPeriod period = repository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el período solicitado"));
        return PayrollPeriodResponse.from(period);
    }

    @Transactional
    public PayrollPeriodResponse create(CreatePayrollPeriodRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();

        if (!request.fechaFin().isAfter(request.fechaInicio())) {
            throw new BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio");
        }

        if (repository.existsByCompanyIdAndEstadoIn(companyId,
                List.of(EstadoPeriodo.ABIERTO, EstadoPeriodo.REABIERTO))) {
            throw new ConflictException("Ya existe un período abierto o reabierto — cerralo antes de abrir uno nuevo");
        }

        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setFechaInicio(request.fechaInicio());
        period.setFechaFin(request.fechaFin());
        period.setEstado(EstadoPeriodo.ABIERTO);

        return PayrollPeriodResponse.from(repository.save(period));
    }
}
