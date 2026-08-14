package com.staffly.backend.payslip.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.staffly.backend.common.pdf.PdfExportAdapter;
import com.staffly.backend.payroll.decorator.DeduccionLinea;
import com.staffly.backend.payslip.TipoRecibo;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class OpenPdfPayslipAdapter implements PdfExportAdapter<PayslipPdfData> {

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat      MONEY_FMT = NumberFormat.getNumberInstance(new Locale("es", "AR"));
    private static final Font FONT_TITLE   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(0x1F, 0x29, 0x37));
    private static final Font FONT_HEADER  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(0x1F, 0x29, 0x37));
    private static final Font FONT_NORMAL  = FontFactory.getFont(FontFactory.HELVETICA,      9,  new Color(0x1F, 0x29, 0x37));
    private static final Font FONT_SMALL   = FontFactory.getFont(FontFactory.HELVETICA,      8,  new Color(0x55, 0x65, 0x81));
    private static final Font FONT_NET     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(0x16, 0xA3, 0x4A));
    private static final Color COLOR_HEADER_BG = new Color(0xF1, 0xF5, 0xF9);
    private static final Color COLOR_NET_BG    = new Color(0xDC, 0xFC, 0xE7);

    static {
        MONEY_FMT.setMinimumFractionDigits(2);
        MONEY_FMT.setMaximumFractionDigits(2);
    }

    @Override
    public byte[] generate(PayslipPdfData d) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, d);
            doc.add(Chunk.NEWLINE);
            addEmployeeInfo(doc, d);
            doc.add(Chunk.NEWLINE);
            if (d.tipo() == TipoRecibo.AJUSTE && d.payslipOriginalId() != null) {
                addAjusteNotice(doc, d);
                doc.add(Chunk.NEWLINE);
            }
            addHaberesTable(doc, d);
            doc.add(Chunk.NEWLINE);
            if (!d.detalleDescuentos().isEmpty()) {
                addDescuentosTable(doc, d);
                doc.add(Chunk.NEWLINE);
            }
            addNetTotal(doc, d);
            doc.add(Chunk.NEWLINE);
            addFooter(doc, d);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF del recibo", e);
        }
    }

    // ── secciones ─────────────────────────────────────────────────────────────

    private void addHeader(Document doc, PayslipPdfData d) throws DocumentException {
        Paragraph title = new Paragraph(
                d.tipo() == TipoRecibo.AJUSTE ? "RECIBO DE SUELDO — AJUSTE" : "RECIBO DE SUELDO",
                FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        Paragraph company = new Paragraph(d.companyNombre() + " — " + d.companyRazonSocial(), FONT_HEADER);
        company.setAlignment(Element.ALIGN_CENTER);
        doc.add(company);

        Paragraph period = new Paragraph(
                "Período: " + d.periodoInicio().format(DATE_FMT) + " al " + d.periodoFin().format(DATE_FMT),
                FONT_NORMAL);
        period.setAlignment(Element.ALIGN_CENTER);
        doc.add(period);
    }

    private void addEmployeeInfo(Document doc, PayslipPdfData d) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 2, 3});

        addLabelCell(table, "Empleado");
        addValueCell(table, d.employeeNombre() + " " + d.employeeApellido());
        addLabelCell(table, "Documento");
        addValueCell(table, d.employeeDocumento());

        addLabelCell(table, "Categoría");
        addValueCell(table, d.employeeCategoria());
        addLabelCell(table, "Sueldo base");
        addValueCell(table, "$ " + money(d.sueldoBase()));

        addLabelCell(table, "Estado recibo");
        addValueCell(table, d.estado().name());
        addLabelCell(table, "Fecha pago");
        addValueCell(table, d.fechaPago() != null ? d.fechaPago().format(DATE_FMT) : "—");

        doc.add(table);
    }

    private void addAjusteNotice(Document doc, PayslipPdfData d) throws DocumentException {
        Paragraph p = new Paragraph(
                "⚠  Recibo de ajuste — anula al recibo original: " + d.payslipOriginalId(),
                FONT_SMALL);
        p.setAlignment(Element.ALIGN_LEFT);
        doc.add(p);
    }

    private void addHaberesTable(Document doc, PayslipPdfData d) throws DocumentException {
        doc.add(new Paragraph("Haberes", FONT_HEADER));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 2});

        addTableHeaderRow(table, "Concepto", "Importe");

        if (d.horasNormales().compareTo(BigDecimal.ZERO) > 0) {
            addTableRow(table, "Horas normales (" + d.horasNormales().toPlainString() + " hs)", "$ " + money(d.brutoCalculado()));
        }
        if (d.horasExtra().compareTo(BigDecimal.ZERO) > 0) {
            addTableRow(table, "Horas extra (" + d.horasExtra().toPlainString() + " hs)", "");
        }
        if (d.horasFeriado().compareTo(BigDecimal.ZERO) > 0) {
            addTableRow(table, "Horas feriado (" + d.horasFeriado().toPlainString() + " hs)", "");
        }
        addTableRow(table, "Bruto calculado", "$ " + money(d.brutoCalculado()));

        doc.add(table);
    }

    private void addDescuentosTable(Document doc, PayslipPdfData d) throws DocumentException {
        doc.add(new Paragraph("Descuentos", FONT_HEADER));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 2});
        addTableHeaderRow(table, "Concepto", "Importe");

        for (DeduccionLinea dl : d.detalleDescuentos()) {
            addTableRow(table, dl.nombre(), "$ " + money(dl.monto()));
        }
        if (d.totalAdelantos().compareTo(BigDecimal.ZERO) > 0) {
            addTableRow(table, "Adelantos descontados", "$ " + money(d.totalAdelantos()));
        }

        doc.add(table);
    }

    private void addNetTotal(Document doc, PayslipPdfData d) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 2});

        PdfPCell labelCell = new PdfPCell(new Phrase("NETO A COBRAR", FONT_NET));
        labelCell.setBackgroundColor(COLOR_NET_BG);
        labelCell.setPadding(8);
        labelCell.setBorder(Rectangle.NO_BORDER);

        PdfPCell valueCell = new PdfPCell(new Phrase("$ " + money(d.netoFinal()), FONT_NET));
        valueCell.setBackgroundColor(COLOR_NET_BG);
        valueCell.setPadding(8);
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(labelCell);
        table.addCell(valueCell);
        doc.add(table);
    }

    private void addFooter(Document doc, PayslipPdfData d) throws DocumentException {
        Paragraph footer = new Paragraph(
                "Emitido: " + d.fechaEmision().format(DATE_FMT) + "   |   ID: " + d.payslipId(),
                FONT_SMALL);
        footer.setAlignment(Element.ALIGN_RIGHT);
        doc.add(footer);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void addLabelCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_SMALL));
        cell.setBackgroundColor(COLOR_HEADER_BG);
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addValueCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_NORMAL));
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addTableHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_HEADER));
            cell.setBackgroundColor(COLOR_HEADER_BG);
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private void addTableRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, FONT_NORMAL));
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, FONT_NORMAL));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String money(BigDecimal amount) {
        return amount != null ? MONEY_FMT.format(amount) : "0,00";
    }
}
