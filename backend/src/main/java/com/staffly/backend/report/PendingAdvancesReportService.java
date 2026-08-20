package com.staffly.backend.report;

import com.staffly.backend.advance.Advance;
import com.staffly.backend.advance.AdvanceRepository;
import com.staffly.backend.advance.EstadoAdelanto;
import com.staffly.backend.report.dto.PendingAdvanceRow;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-24: adelantos pendientes de descuento, ordenados por fecha ascendente. */
@Service
public class PendingAdvancesReportService {

    private final AdvanceRepository advanceRepository;

    public PendingAdvancesReportService(AdvanceRepository advanceRepository) {
        this.advanceRepository = advanceRepository;
    }

    @Transactional(readOnly = true)
    public List<PendingAdvanceRow> generate(UUID companyId) {
        return advanceRepository.findByCompanyIdAndEstado(companyId, EstadoAdelanto.PENDIENTE).stream()
                .map(this::toRow)
                .toList();
    }

    private PendingAdvanceRow toRow(Advance a) {
        return new PendingAdvanceRow(
                a.getId(),
                a.getEmployee().getId(),
                a.getEmployee().getNombre(),
                a.getEmployee().getApellido(),
                a.getMonto(),
                a.getFecha(),
                a.getMotivo()
        );
    }
}
