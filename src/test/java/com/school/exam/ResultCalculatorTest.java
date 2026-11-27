package com.school.exam;

import com.school.academic.Subject;
import com.school.exam.ExamDtos.StudentResultRow;
import com.school.grading.Gpa5GradingStrategy;
import com.school.student.Student;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCalculatorTest {

    private final ResultCalculator calculator = new ResultCalculator();
    private final Gpa5GradingStrategy gpa5 = new Gpa5GradingStrategy();

    @Test
    void calculate_missingMarkCountsAsAbsentAndFails() {
        Subject math = subject(1L, "MATH", 100, 33);
        Subject eng = subject(2L, "ENG", 100, 33);
        Student student = student(10L, 1, "ADM-1", "Amina", "Islam");
        Mark mathMark = new Mark(null, student, math, new BigDecimal("85"), false);

        // ENG mark is missing -> counts as absent and fails overall in GPA_5
        StudentResultRow row = calculator.calculateStudent(student, List.of(math, eng), Map.of(1L, mathMark), gpa5);

        assertThat(row.passed()).isFalse();
        assertThat(row.gpa()).isEqualByComparingTo("0.00");
        assertThat(row.letter()).isEqualTo("F");
        assertThat(row.subjects()).hasSize(2);
        assertThat(row.subjects().get(1).absent()).isTrue();
        assertThat(row.subjects().get(1).obtained()).isNull();
        assertThat(row.subjects().get(1).passed()).isFalse();
    }

    @Test
    void rank_tiesShareAPosition_andTheNextPositionIsSkipped() {
        List<StudentResultRow> ranked = calculator.rank(List.of(
                row(1L, 1, "80.00", "4.50", true),
                row(2L, 2, "90.00", "5.00", true),
                row(3L, 3, "90.00", "5.00", true)
        ));

        assertThat(ranked).extracting(StudentResultRow::studentId).containsExactly(2L, 3L, 1L);
        assertThat(ranked).extracting(StudentResultRow::position).containsExactly(1, 1, 3);
    }

    @Test
    void rank_failedStudentsGetNoPositionAndComeLast() {
        List<StudentResultRow> ranked = calculator.rank(List.of(
                row(1L, 1, "65.00", "0.00", false),
                row(2L, 2, "55.00", "3.00", true),
                row(3L, 3, "75.00", "0.00", false)
        ));

        assertThat(ranked).extracting(StudentResultRow::studentId).containsExactly(2L, 3L, 1L);
        assertThat(ranked).extracting(StudentResultRow::position).containsExactly(1, null, null);
    }

    @Test
    void rank_sameGpa_higherPercentageRanksFirst() {
        List<StudentResultRow> ranked = calculator.rank(List.of(
                row(1L, 1, "82.00", "5.00", true),
                row(2L, 2, "94.50", "5.00", true),
                row(3L, 3, "88.00", "5.00", true)
        ));

        assertThat(ranked).extracting(StudentResultRow::studentId).containsExactly(2L, 3L, 1L);
        assertThat(ranked).extracting(StudentResultRow::position).containsExactly(1, 2, 3);
    }

    private Subject subject(Long id, String code, int full, int pass) {
        Subject s = new Subject(code, code, full, pass);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private Student student(Long id, int roll, String adm, String first, String last) {
        Student s = new Student(adm);
        ReflectionTestUtils.setField(s, "id", id);
        s.setRollNo(roll);
        s.setFirstName(first);
        s.setLastName(last);
        return s;
    }

    private StudentResultRow row(Long id, int roll, String pct, String gpa, boolean passed) {
        return new StudentResultRow(
                id, roll, "ADM-" + id, "Student " + id,
                new BigDecimal(pct), 100, new BigDecimal(pct),
                new BigDecimal(gpa), passed ? "A+" : "F", passed, null, List.of()
        );
    }
}
