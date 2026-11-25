package com.school.student;

import com.school.academic.AcademicYear;
import com.school.academic.SchoolClass;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentCsvExporterTest {

    private final StudentCsvExporter exporter = new StudentCsvExporter();

    @Test
    void quote_escapesQuotesAndKeepsCommasInsideOneCell() {
        assertThat(StudentCsvExporter.quote("Uddin, Rahim")).isEqualTo("\"Uddin, Rahim\"");
        assertThat(StudentCsvExporter.quote("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(StudentCsvExporter.quote("line\r\nbreak")).isEqualTo("\"line\r\nbreak\"");
    }

    @Test
    void quote_neutralisesSpreadsheetFormulas() {
        assertThat(StudentCsvExporter.quote("=1+1")).isEqualTo("\"'=1+1\"");
        assertThat(StudentCsvExporter.quote("+8801712345678")).isEqualTo("\"'+8801712345678\"");
        assertThat(StudentCsvExporter.quote("-5")).isEqualTo("\"'-5\"");
        assertThat(StudentCsvExporter.quote("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(StudentCsvExporter.quote("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        // Only the first character matters.
        assertThat(StudentCsvExporter.quote("a=b")).isEqualTo("\"a=b\"");
    }

    @Test
    void quote_nullBecomesEmptyCell() {
        assertThat(StudentCsvExporter.quote(null)).isEqualTo("\"\"");
        assertThat(StudentCsvExporter.quote("")).isEqualTo("\"\"");
    }

    @Test
    void toCsv_writesHeaderAndOneQuotedRowPerStudent_withCrlf() {
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        Student placed = student("ADM-1", "Rahim", "Uddin");
        placed.setSchoolClass(new SchoolClass(year, "Class 6", "A", null, 40));
        placed.setRollNo(3);
        Student unplaced = student("ADM-2", "Karim", "Ali");

        String csv = exporter.toCsv(List.of(placed, unplaced));

        assertThat(csv.split("\r\n", -1)).containsExactly(
                StudentCsvExporter.HEADER,
                "\"ADM-1\",\"Rahim\",\"Uddin\",\"MALE\",\"2014-03-09\",\"Class 6 - A\",\"3\",\"ACTIVE\","
                        + "\"\",\"\",\"Abdul Uddin\",\"01711111111\",\"Father\",\"2026-01-05\"",
                "\"ADM-2\",\"Karim\",\"Ali\",\"MALE\",\"2014-03-09\",\"\",\"\",\"ACTIVE\","
                        + "\"\",\"\",\"Abdul Uddin\",\"01711111111\",\"Father\",\"2026-01-05\"",
                "");
    }

    private static Student student(String admissionNo, String firstName, String lastName) {
        Student student = new Student(admissionNo);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setGender(Gender.MALE);
        student.setDateOfBirth(LocalDate.of(2014, 3, 9));
        student.setGuardianName("Abdul Uddin");
        student.setGuardianPhone("01711111111");
        student.setGuardianRelation("Father");
        student.setAdmissionDate(LocalDate.of(2026, 1, 5));
        return student;
    }
}
