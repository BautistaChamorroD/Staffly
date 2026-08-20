package com.staffly.backend.report;

import com.staffly.backend.branch.Branch;
import com.staffly.backend.payslip.Payslip;
import com.staffly.backend.payslip.PayslipRepository;
import com.staffly.backend.report.dto.PayrollCostRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-23: costo de nómina por sucursal/empresa/período, sobre {@link Payslip}
 * de períodos CERRADOS únicamente — a diferencia de {@code HoursWorkedReportService},
 * que calcula on-demand sobre {@code Schedule} sin depender del cierre.
 *
 * <p>{@code Payslip} no tiene sucursal propia: la relación es
 * {@code Employee}↔{@code Branch} M2M. Un empleado con exactamente una
 * sucursal asignada imputa su costo ahí; un empleado con 0 o 2+ sucursales
 * cae en un grupo aparte ({@code branchId=null}, "Sin sucursal única") — no
 * se inventa un criterio de reparto proporcional. Decisión consciente
 * (confirmada con el usuario), documentada acá y no un bug.
 */
@Service
public class PayrollCostReportService {

    private final PayslipRepository payslipRepository;

    public PayrollCostReportService(PayslipRepository payslipRepository) {
        this.payslipRepository = payslipRepository;
    }

    @Transactional(readOnly = true)
    public List<PayrollCostRow> generate(UUID companyId, UUID branchIdFilter, LocalDate desde, LocalDate hasta) {
        List<Payslip> payslips = payslipRepository.findClosedForReport(companyId, desde, hasta);

        Map<UUID, Branch> branchPorId = new LinkedHashMap<>();
        Map<UUID, List<Payslip>> porSucursal = new LinkedHashMap<>();
        for (Payslip p : payslips) {
            Set<Branch> branches = p.getEmployee().getBranches();
            UUID key = branches.size() == 1 ? branches.iterator().next().getId() : null;
            if (key != null) {
                branchPorId.putIfAbsent(key, branches.iterator().next());
            }
            if (branchIdFilter != null && !branchIdFilter.equals(key)) {
                continue;
            }
            porSucursal.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        List<PayrollCostRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, List<Payslip>> entry : porSucursal.entrySet()) {
            rows.add(calcularFila(entry.getKey(), branchPorId.get(entry.getKey()), entry.getValue()));
        }
        return rows;
    }

    private PayrollCostRow calcularFila(UUID branchId, Branch branch, List<Payslip> payslipsDeLaSucursal) {
        BigDecimal totalBruto = BigDecimal.ZERO;
        BigDecimal totalDeducciones = BigDecimal.ZERO;
        BigDecimal totalNeto = BigDecimal.ZERO;
        Set<UUID> empleados = new java.util.HashSet<>();

        for (Payslip p : payslipsDeLaSucursal) {
            totalBruto = totalBruto.add(p.getBrutoCalculado());
            totalDeducciones = totalDeducciones.add(p.getTotalDeducciones());
            totalNeto = totalNeto.add(p.getNetoFinal());
            empleados.add(p.getEmployee().getId());
        }

        return new PayrollCostRow(
                branchId,
                branch != null ? branch.getNombre() : "Sin sucursal única",
                empleados.size(),
                totalBruto.setScale(2, RoundingMode.HALF_UP),
                totalDeducciones.setScale(2, RoundingMode.HALF_UP),
                totalNeto.setScale(2, RoundingMode.HALF_UP)
        );
    }
}
