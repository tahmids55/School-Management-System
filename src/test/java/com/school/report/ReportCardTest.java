package com.school.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportCardTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-11-27T09:30:00Z");

    @Test
    void builder_requiresTheStudentName() {
        ReportCard.Builder builder = ReportCard.builder()
                .schoolName("Greenfield High School")
                .examName("Annual Exam")
                .letter("A+")
                .addLine(new ReportCard.Line("MATH", "Mathematics", 100, "90", "A+", new BigDecimal("5.00"), true))
                .generatedAt(GENERATED_AT);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("studentName");
    }

    @Test
    void builder_requiresAtLeastOneSubject() {
        ReportCard.Builder builder = ReportCard.builder()
                .schoolName("Greenfield High School")
                .examName("Annual Exam")
                .studentName("Amina Islam")
                .letter("A+")
                .generatedAt(GENERATED_AT);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A report card needs at least one subject");
    }

    @Test
    void factory_createsTheRightRenderer() {
        ReportRendererFactory factory = new ReportRendererFactory();

        ReportCardRenderer pdf1 = factory.create(ReportFormat.PDF);
        ReportCardRenderer pdf2 = factory.create(ReportFormat.PDF);
        ReportCardRenderer csv1 = factory.create(ReportFormat.CSV);

        assertThat(pdf1).isInstanceOf(PdfReportCardRenderer.class);
        assertThat(pdf1).isNotSameAs(pdf2);
        assertThat(csv1).isInstanceOf(CsvReportCardRenderer.class);
    }

    @Test
    void csvRenderer_writesAllSectionsAndQuotesCommas() {
        ReportCard card = sampleCard("Islam, Amina", "=SUM(A1:A2)");

        byte[] bytes = new CsvReportCardRenderer().render(card);
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertThat(csv).contains("\r\n");
        assertThat(csv).contains("\"School\",\"Greenfield High School\"\r\n");
        assertThat(csv).contains("\"Exam\",\"Annual Exam (2026)\"\r\n");
        assertThat(csv).contains("\"Grading\",\"GPA 5.0 (Bangladesh)\"\r\n");
        assertThat(csv).contains("\"Student\",\"Islam, Amina\"\r\n");
        assertThat(csv).contains("\"Admission No\",\"ADM-2026-001\"\r\n");
        assertThat(csv).contains("\"Class\",\"Class 9 - A\"\r\n");
        assertThat(csv).contains("\"Roll\",\"1\"\r\n");
        assertThat(csv).contains("\"Subject Code\",\"Subject\",\"Full Marks\",\"Obtained\",\"Grade\",\"Point\",\"Result\"\r\n");
        assertThat(csv).contains("\"MATH\",\"'=SUM(A1:A2)\",\"100\",\"88\",\"A+\",\"5.00\",\"PASS\"\r\n");
        assertThat(csv).contains("\"ENG\",\"English\",\"100\",\"AB\",\"F\",\"0.00\",\"FAIL\"\r\n");
        assertThat(csv).contains("\"Total\",\"88 / 200\"\r\n");
        assertThat(csv).contains("\"Percentage\",\"44.00%\"\r\n");
        assertThat(csv).contains("\"GPA\",\"0.00\"\r\n");
        assertThat(csv).contains("\"Grade\",\"F\"\r\n");
        assertThat(csv).contains("\"Result\",\"FAILED\"\r\n");
        assertThat(csv).contains("\"Position\",\"-\"\r\n");
    }

    @Test
    void pdfRenderer_producesAPdfDocument() {
        ReportCard card = sampleCard("Amina Islam", "Mathematics");

        byte[] bytes = new PdfReportCardRenderer().render(card);

        assertThat(bytes.length).isGreaterThan(500);
        String header = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }

    private ReportCard sampleCard(String studentName, String mathName) {
        return ReportCard.builder()
                .schoolName("Greenfield High School")
                .examName("Annual Exam")
                .academicYearName("2026")
                .gradingScheme("GPA 5.0 (Bangladesh)")
                .studentName(studentName)
                .admissionNo("ADM-2026-001")
                .className("Class 9 - A")
                .rollNo(1)
                .lines(List.of(
                        new ReportCard.Line("MATH", mathName, 100, "88", "A+", new BigDecimal("5.00"), true),
                        new ReportCard.Line("ENG", "English", 100, "AB", "F", new BigDecimal("0.00"), false)
                ))
                .totalObtained(new BigDecimal("88"))
                .totalFullMarks(200)
                .percentage(new BigDecimal("44.00"))
                .gpa(new BigDecimal("0.00"))
                .letter("F")
                .passed(false)
                .position(null)
                .classSize(30)
                .generatedAt(GENERATED_AT)
                .build();
    }
}
