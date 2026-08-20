package com.staffly.backend.report.csv;

import com.staffly.backend.report.dto.ReportTable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/** Serialización CSV simple (RFC 4180) de un {@link ReportTable}, sin librería externa. */
@Component
public class CsvReportWriter {

    public byte[] write(ReportTable table) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, table.headers());
        for (List<String> row : table.rows()) {
            appendRow(sb, row);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(values.get(i)));
        }
        sb.append("\r\n");
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
