package com.staffly.backend.report;

import com.staffly.backend.common.BadRequestException;
import com.staffly.backend.common.pdf.PdfExportAdapter;
import com.staffly.backend.report.csv.CsvReportWriter;
import com.staffly.backend.report.dto.HoursWorkedRow;
import com.staffly.backend.report.dto.PayrollCostRow;
import com.staffly.backend.report.dto.PendingAdvanceRow;
import com.staffly.backend.report.dto.ReportTable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-25: exportación PDF/CSV de los 3 reportes de RF-22/23/24, sobre una
 * representación tabular genérica ({@link ReportTable}) para no duplicar
 * lógica de exportación por tipo de reporte (AUD-33 / issue #154).
 */
@Service
public class ReportExportService {

    private final HoursWorkedReportService     hoursWorkedReportService;
    private final PayrollCostReportService     payrollCostReportService;
    private final PendingAdvancesReportService pendingAdvancesReportService;
    private final PdfExportAdapter<ReportTable> pdfAdapter;
    private final CsvReportWriter               csvWriter;

    public ReportExportService(HoursWorkedReportService hoursWorkedReportService,
                               PayrollCostReportService payrollCostReportService,
                               PendingAdvancesReportService pendingAdvancesReportService,
                               PdfExportAdapter<ReportTable> pdfAdapter,
                               CsvReportWriter csvWriter) {
        this.hoursWorkedReportService = hoursWorkedReportService;
        this.payrollCostReportService = payrollCostReportService;
        this.pendingAdvancesReportService = pendingAdvancesReportService;
        this.pdfAdapter = pdfAdapter;
        this.csvWriter = csvWriter;
    }

    @Transactional(readOnly = true)
    public byte[] export(String report, String format, UUID companyId, UUID branchId, LocalDate desde, LocalDate hasta) {
        if (!"pdf".equals(format) && !"csv".equals(format)) {
            throw new BadRequestException("Formato de exportación inválido — usá 'pdf' o 'csv'");
        }

        ReportTable table = buildTable(report, companyId, branchId, desde, hasta);
        return "pdf".equals(format) ? pdfAdapter.generate(table) : csvWriter.write(table);
    }

    private ReportTable buildTable(String report, UUID companyId, UUID branchId, LocalDate desde, LocalDate hasta) {
        return switch (report) {
            case "hours-worked" -> toTable(
                    "Horas trabajadas",
                    List.of("employeeId", "employeeNombre", "employeeApellido", "branchId", "branchNombre",
                            "horasNormales", "horasExtra", "horasFeriado", "totalHoras"),
                    hoursWorkedReportService.generate(companyId, branchId, desde, hasta),
                    (HoursWorkedRow r) -> List.of(
                            String.valueOf(r.employeeId()), r.employeeNombre(), r.employeeApellido(),
                            String.valueOf(r.branchId()), r.branchNombre(),
                            r.horasNormales().toPlainString(), r.horasExtra().toPlainString(),
                            r.horasFeriado().toPlainString(), r.totalHoras().toPlainString()));
            case "payroll-cost" -> toTable(
                    "Costo de nómina",
                    List.of("branchId", "branchNombre", "cantidadEmpleados", "totalBruto", "totalDeducciones", "totalNeto"),
                    payrollCostReportService.generate(companyId, branchId, desde, hasta),
                    (PayrollCostRow r) -> List.of(
                            r.branchId() != null ? r.branchId().toString() : "", r.branchNombre(),
                            String.valueOf(r.cantidadEmpleados()),
                            r.totalBruto().toPlainString(), r.totalDeducciones().toPlainString(), r.totalNeto().toPlainString()));
            case "pending-advances" -> toTable(
                    "Adelantos pendientes",
                    List.of("id", "employeeId", "employeeNombre", "employeeApellido", "monto", "fecha", "motivo"),
                    pendingAdvancesReportService.generate(companyId),
                    (PendingAdvanceRow r) -> List.of(
                            r.id().toString(), r.employeeId().toString(), r.employeeNombre(), r.employeeApellido(),
                            r.monto().toPlainString(), r.fecha().toString(), r.motivo() != null ? r.motivo() : ""));
            default -> throw new BadRequestException("Reporte desconocido: " + report);
        };
    }

    private <T> ReportTable toTable(String titulo, List<String> headers, List<T> rows,
                                    java.util.function.Function<T, List<String>> mapper) {
        return new ReportTable(titulo, headers, rows.stream().map(mapper).toList());
    }
}
