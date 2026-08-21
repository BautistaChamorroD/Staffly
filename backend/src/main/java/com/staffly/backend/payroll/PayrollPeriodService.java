package com.staffly.backend.payroll;

import com.staffly.backend.advance.AdvanceRepository;
import com.staffly.backend.advance.EstadoAdelanto;
import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.ConflictException;
import com.staffly.backend.common.ResourceNotFoundException;
import com.staffly.backend.common.UnprocessableEntityException;
import com.staffly.backend.payroll.dto.CreatePayrollPeriodRequest;
import com.staffly.backend.payroll.dto.PayrollPeriodResponse;
import com.staffly.backend.payslip.EstadoRecibo;
import com.staffly.backend.payslip.Payslip;
import com.staffly.backend.payslip.PayslipRepository;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PayrollPeriodService {

    private final PayrollPeriodRepository repository;
    private final PayslipRepository       payslipRepository;
    private final AdvanceRepository       advanceRepository;

    public PayrollPeriodService(PayrollPeriodRepository repository,
                                PayslipRepository payslipRepository,
                                AdvanceRepository advanceRepository) {
        this.repository        = repository;
        this.payslipRepository = payslipRepository;
        this.advanceRepository = advanceRepository;
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

    @Transactional
    public PayrollPeriodResponse reopen(UUID id, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();

        PayrollPeriod period = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el período"));

        if (period.getEstado() != EstadoPeriodo.CERRADO) {
            throw new ConflictException("Solo se puede reabrir un período CERRADO");
        }

        if (repository.existsByCompanyIdAndFechaInicioAfterAndEstado(
                companyId, period.getFechaFin(), EstadoPeriodo.CERRADO)) {
            throw new UnprocessableEntityException(
                    "No se puede reabrir el período: existe un período posterior ya cerrado");
        }

        period.setEstado(EstadoPeriodo.REABIERTO);
        period.setFechaCierre(null);

        List<Payslip> payslips = payslipRepository.findByCompanyIdAndPayrollPeriodId(companyId, id);
        payslips.forEach(p -> {
            p.setEstado(EstadoRecibo.GENERADO);
            p.setFechaPago(null);

            // revertir los adelantos que este recibo había descontado — si no se
            // revierten, quedan DESCONTADO sin haberse pagado nunca (issue #147).
            for (UUID advanceId : p.getAdelantosAplicados()) {
                advanceRepository.findByIdAndCompanyId(advanceId, companyId)
                        .filter(a -> a.getEstado() == EstadoAdelanto.DESCONTADO)
                        .filter(a -> a.getPayrollPeriod() == null
                                || a.getPayrollPeriod().getId().equals(period.getId()))
                        .ifPresent(a -> {
                            a.setEstado(EstadoAdelanto.PENDIENTE);
                            a.setPayrollPeriod(null);
                        });
            }
        });

        return PayrollPeriodResponse.from(repository.save(period));
    }
}
