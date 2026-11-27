package com.school.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * RFC 4180 CSV renderer: every cell quoted, CRLF line endings, formula prefixes neutralised.
 */
public class CsvReportCardRenderer extends ReportCardRenderer {

    private static final String CRLF = "\r\n";

    private StringBuilder out;

    @Override
    protected void begin(ReportCard card) {
        this.out = new StringBuilder();
    }

    @Override
    protected void writeHeader(ReportCard card) {
        row("School", card.getSchoolName());
        row("Exam", examLabel(card));
        row("Grading", card.getGradingScheme());
    }

    @Override
    protected void writeStudentDetails(ReportCard card) {
        row("Student", card.getStudentName());
        row("Admission No", card.getAdmissionNo());
        row("Class", card.getClassName());
        row("Roll", card.getRollNo() == null ? "-" : String.valueOf(card.getRollNo()));
    }

    @Override
    protected void writeSubjects(ReportCard card) {
        blankLine();
        row("Subject Code", "Subject", "Full Marks", "Obtained", "Grade", "Point", "Result");
        for (ReportCard.Line line : card.getLines()) {
            row(
                    line.subjectCode(),
                    line.subjectName(),
                    String.valueOf(line.fullMarks()),
                    line.obtained(),
                    line.letter(),
                    format2(line.point()),
                    line.passed() ? "PASS" : "FAIL"
            );
        }
    }

    @Override
    protected void writeSummary(ReportCard card) {
        blankLine();
        row("Total", card.getTotalObtained().stripTrailingZeros().toPlainString() + " / " + card.getTotalFullMarks());
        row("Percentage", format2(card.getPercentage()) + "%");
        row("GPA", format2(card.getGpa()));
        row("Grade", card.getLetter());
        row("Result", card.isPassed() ? "PASSED" : "FAILED");
        row("Position", positionText(card));
    }

    @Override
    protected byte[] finish() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void row(String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(values[i]));
        }
        out.append(CRLF);
    }

    private void blankLine() {
        out.append(CRLF);
    }

    static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        String safe = value;
        if (!safe.isEmpty()) {
            char first = safe.charAt(0);
            if ("=+@\t\r".indexOf(first) >= 0 || (first == '-' && safe.length() > 1)) {
                safe = "'" + safe;
            }
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String examLabel(ReportCard card) {
        return card.getAcademicYearName().isBlank()
                ? card.getExamName()
                : card.getExamName() + " (" + card.getAcademicYearName() + ")";
    }

    private static String format2(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
