package com.school.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * OpenPDF renderer producing an A4 report card with Dhaka-timezone footer.
 */
public class PdfReportCardRenderer extends ReportCardRenderer {

    private static final Color BRAND_PURPLE = new Color(0x56, 0x45, 0xD4);
    private static final Color HEADER_GREY = new Color(0xEC, 0xEE, 0xF5);
    private static final Color BORDER_GREY = new Color(0xD9, 0xDF, 0xEA);
    private static final Color MUTED_TEXT = new Color(0x5B, 0x64, 0x76);

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_PURPLE);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, MUTED_TEXT);
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font TABLE_BODY_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, MUTED_TEXT);

    private static final DateTimeFormatter DHAKA_FOOTER_FORMAT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Dhaka"));

    private ByteArrayOutputStream buffer;
    private Document document;

    @Override
    protected void begin(ReportCard card) {
        try {
            buffer = new ByteArrayOutputStream();
            document = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(document, buffer);
            document.open();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Could not build the PDF report card", ex);
        }
    }

    @Override
    protected void writeHeader(ReportCard card) {
        try {
            Paragraph title = new Paragraph(card.getSchoolName(), TITLE_FONT);
            title.setSpacingAfter(4);
            document.add(title);

            String subtitleText = card.getAcademicYearName().isBlank()
                    ? "Report Card - " + card.getExamName()
                    : "Report Card - " + card.getExamName() + " (" + card.getAcademicYearName() + ")";
            Paragraph subtitle = new Paragraph(subtitleText, SUBTITLE_FONT);
            subtitle.setSpacingAfter(14);
            document.add(subtitle);
        } catch (DocumentException ex) {
            throw new IllegalStateException("Could not build the PDF report card", ex);
        }
    }

    @Override
    protected void writeStudentDetails(ReportCard card) {
        try {
            PdfPTable grid = new PdfPTable(new float[]{1.2f, 2.3f, 1.2f, 1.8f});
            grid.setWidthPercentage(100);
            grid.setSpacingAfter(14);

            addKeyValue(grid, "Student", card.getStudentName());
            addKeyValue(grid, "Admission No", card.getAdmissionNo());
            addKeyValue(grid, "Class", card.getClassName());
            addKeyValue(grid, "Roll", card.getRollNo() == null ? "-" : String.valueOf(card.getRollNo()));
            addKeyValue(grid, "Grading", card.getGradingScheme());
            addKeyValue(grid, "Position", positionText(card));

            document.add(grid);
        } catch (DocumentException ex) {
            throw new IllegalStateException("Could not build the PDF report card", ex);
        }
    }

    @Override
    protected void writeSubjects(ReportCard card) {
        try {
            PdfPTable table = new PdfPTable(new float[]{1.2f, 2.8f, 1.0f, 1.1f, 0.9f, 0.9f, 1.1f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(14);

            for (String header : new String[]{"Code", "Subject", "Full", "Obtained", "Grade", "Point", "Result"}) {
                PdfPCell cell = new PdfPCell(new Phrase(header, TABLE_HEADER_FONT));
                cell.setBackgroundColor(HEADER_GREY);
                cell.setBorderColor(BORDER_GREY);
                cell.setPadding(6);
                table.addCell(cell);
            }

            for (ReportCard.Line line : card.getLines()) {
                addTableCell(table, line.subjectCode(), Element.ALIGN_LEFT);
                addTableCell(table, line.subjectName(), Element.ALIGN_LEFT);
                addTableCell(table, String.valueOf(line.fullMarks()), Element.ALIGN_RIGHT);
                addTableCell(table, line.obtained(), Element.ALIGN_RIGHT);
                addTableCell(table, line.letter(), Element.ALIGN_CENTER);
                addTableCell(table, format2(line.point()), Element.ALIGN_RIGHT);
                addTableCell(table, line.passed() ? "PASS" : "FAIL", Element.ALIGN_CENTER);
            }

            document.add(table);
        } catch (DocumentException ex) {
            throw new IllegalStateException("Could not build the PDF report card", ex);
        }
    }

    @Override
    protected void writeSummary(ReportCard card) {
        try {
            PdfPTable summary = new PdfPTable(new float[]{1.3f, 2.0f, 1.3f, 2.0f});
            summary.setWidthPercentage(100);
            summary.setSpacingAfter(18);

            String totalText = card.getTotalObtained().stripTrailingZeros().toPlainString()
                    + " / " + card.getTotalFullMarks();
            addKeyValue(summary, "Total", totalText);
            addKeyValue(summary, "Percentage", format2(card.getPercentage()) + "%");
            addKeyValue(summary, "GPA", format2(card.getGpa()));
            addKeyValue(summary, "Grade", card.getLetter());
            addKeyValue(summary, "Result", card.isPassed() ? "PASSED" : "FAILED");
            addKeyValue(summary, "Position", positionText(card));

            document.add(summary);
        } catch (DocumentException ex) {
            throw new IllegalStateException("Could not build the PDF report card", ex);
        }
    }

    @Override
    protected void writeFooter(ReportCard card) {
        try {
            String footerText = "Generated " + DHAKA_FOOTER_FORMAT.format(card.getGeneratedAt())
                    + ". This is a computer-generated document.";
            Paragraph footer = new Paragraph(footerText, FOOTER_FONT);
            document.add(footer);
        } catch (DocumentException ex) {
            throw new IllegalStateException("Could not build the PDF report card", ex);
        }
    }

    @Override
    protected byte[] finish() {
        document.close();
        return buffer.toByteArray();
    }

    private static void addKeyValue(PdfPTable table, String label, String value) {
        PdfPCell keyCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        keyCell.setBorder(Rectangle.BOX);
        keyCell.setBorderColor(BORDER_GREY);
        keyCell.setBackgroundColor(HEADER_GREY);
        keyCell.setPadding(5);
        table.addCell(keyCell);

        PdfPCell valCell = new PdfPCell(new Phrase(value == null ? "-" : value, VALUE_FONT));
        valCell.setBorder(Rectangle.BOX);
        valCell.setBorderColor(BORDER_GREY);
        valCell.setPadding(5);
        table.addCell(valCell);
    }

    private static void addTableCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "-" : text, TABLE_BODY_FONT));
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(BORDER_GREY);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static String format2(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
