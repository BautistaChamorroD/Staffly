package com.staffly.backend.payroll;

import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.payroll.dto.PayrollConfigResponse;
import com.staffly.backend.payroll.dto.UpdatePayrollConfigRequest;
import com.staffly.backend.security.StafflyUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PayrollConfigService {

    private final PayrollConfigRepository repository;

    public PayrollConfigService(PayrollConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PayrollConfigResponse get(StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        PayrollConfig config = repository.findByCompanyId(companyId)
                .orElseGet(() -> createDefault(companyId));
        return PayrollConfigResponse.from(config);
    }

    @Transactional
    public PayrollConfigResponse update(UpdatePayrollConfigRequest request, StafflyUserPrincipal principal) {
        UUID companyId = principal.getCompanyId();
        PayrollConfig config = repository.findByCompanyId(companyId)
                .orElseGet(() -> createDefault(companyId));

        config.setUmbralHorasExtra(request.umbralHorasExtra());
        config.setTipoUmbral(request.tipoUmbral());
        config.setMultiplicadorHoraExtra(request.multiplicadorHoraExtra());
        config.setMultiplicadorFeriado(request.multiplicadorFeriado());
        config.setPeriodicidad(request.periodicidad());

        List<ConceptoDescuento> conceptos = request.conceptosDescuento() == null
                ? List.of()
                : request.conceptosDescuento().stream()
                        .map(c -> {
                            TipoConcepto tipo;
                            try {
                                tipo = TipoConcepto.valueOf(c.tipo());
                            } catch (IllegalArgumentException e) {
                                throw new BadRequestException("Tipo de concepto inválido: " + c.tipo());
                            }
                            return new ConceptoDescuento(c.nombre().strip(), tipo, c.valor());
                        })
                        .toList();
        config.setConceptosDescuento(conceptos);

        return PayrollConfigResponse.from(repository.save(config));
    }

    private PayrollConfig createDefault(UUID companyId) {
        PayrollConfig config = new PayrollConfig();
        config.setCompanyId(companyId);
        config.setUmbralHorasExtra(BigDecimal.valueOf(8));
        config.setTipoUmbral(TipoUmbral.DIARIO);
        config.setMultiplicadorHoraExtra(BigDecimal.valueOf(1.5));
        config.setMultiplicadorFeriado(BigDecimal.valueOf(2.0));
        config.setPeriodicidad(Periodicidad.MENSUAL);
        config.setConceptosDescuento(List.of());
        return repository.save(config);
    }
}
