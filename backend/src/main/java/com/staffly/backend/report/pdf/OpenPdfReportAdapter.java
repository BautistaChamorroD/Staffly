package com.staffly.backend.report.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.staffly.backend.common.pdf.PdfExportAdapter;
import com.staffly.backend.report.dto.ReportTable;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adapter genérico que renderiza cualquier {@link ReportTable} como PDF —
 * reusa el mismo {@link PdfExportAdapter} que {@code OpenPdfPayslipAdapter},
 * pero sin acoplarse a un tipo de fila específico (AUD-33 / issue #154).
 */
@Component
public class OpenPdfReportAdapter implements PdfExportAdapter<ReportTable> {

    private static final Font FONT_TITLE  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(0x1F, 0x29, 0x37));
    private static final Font FONT_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(0x1F, 0x29, 0x37));
    private static final Font FONT_NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x1F, 0x29, 0x37));
    private static final Color COLOR_HEADER_BG = new Color(0xF1, 0xF5, 0xF9);

    @Override
    public byte[] generate(ReportTable table) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 40, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph(table.titulo(), FONT_TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(Chunk.NEWLINE);

            PdfPTable pdfTable = new PdfPTable(Math.max(table.headers().size(), 1));
            pdfTable.setWidthPercentage(100);

            for (String header : table.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(header, FONT_HEADER));
                cell.setBackgroundColor(COLOR_HEADER_BG);
                cell.setPadding(6);
                pdfTable.addCell(cell);
            }

            for (List<String> row : table.rows()) {
                for (String value : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, FONT_NORMAL));
                    cell.setPadding(5);
                    pdfTable.addCell(cell);
                }
            }

            doc.add(pdfTable);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF del reporte", e);
        }
    }
}
