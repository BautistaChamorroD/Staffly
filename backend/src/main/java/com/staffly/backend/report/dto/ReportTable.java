package com.staffly.backend.report.dto;

import java.util.List;

/**
 * Representación tabular genérica de un reporte, independiente del tipo de
 * fila original — permite reusar un único adapter de PDF y un único writer
 * de CSV para los 3 reportes de RF-22/23/24 (AUD-33 / issue #154).
 */
public record ReportTable(String titulo, List<String> headers, List<List<String>> rows) {}
