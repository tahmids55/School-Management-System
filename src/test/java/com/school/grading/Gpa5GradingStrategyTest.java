package com.school.grading;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Gpa5GradingStrategyTest {

    private final Gpa5GradingStrategy strategy = new Gpa5GradingStrategy();

    @ParameterizedTest
    @CsvSource({
            "100, A+, 5.00, true",
            "80,  A+, 5.00, true",
            "79,  A,  4.00, true",
            "70,  A,  4.00, true",
            "69,  A-, 3.50, true",
            "60,  A-, 3.50, true",
            "59,  B,  3.00, true",
            "50,  B,  3.00, true",
            "49,  C,  2.00, true",
            "40,  C,  2.00, true",
            "39,  D,  1.00, true",
            "33,  D,  1.00, true",
            "32,  F,  0.00, false",
            "0,   F,  0.00, false"
    })
    void gradeSubject_appliesTheOfficialBands(
            String marks,
            String expectedLetter,
            String expectedPoint,
            boolean expectedPassed
    ) {
        SubjectGrade grade = strategy.gradeSubject(new BigDecimal(marks), 100, 33, false);

        assertThat(grade.letter()).isEqualTo(expectedLetter);
        assertThat(grade.point()).isEqualByComparingTo(expectedPoint);
        assertThat(grade.passed()).isEqualTo(expectedPassed);
        assertThat(grade.absent()).isFalse();
        assertThat(grade.percentage()).isEqualByComparingTo(new BigDecimal(marks));
    }

    @Test
    void gradeSubject_usesPercentageForSubjectsWithOtherFullMarks() {
        SubjectGrade fortyOfFifty = strategy.gradeSubject(new BigDecimal("40"), 50, 17, false);
        assertThat(fortyOfFifty.percentage()).isEqualByComparingTo("80.00");
        assertThat(fortyOfFifty.letter()).isEqualTo("A+");
        assertThat(fortyOfFifty.point()).isEqualByComparingTo("5.00");
        assertThat(fortyOfFifty.passed()).isTrue();

        SubjectGrade thirtyFiveOfFifty = strategy.gradeSubject(new BigDecimal("35"), 50, 17, false);
        assertThat(thirtyFiveOfFifty.percentage()).isEqualByComparingTo("70.00");
        assertThat(thirtyFiveOfFifty.letter()).isEqualTo("A");
        assertThat(thirtyFiveOfFifty.point()).isEqualByComparingTo("4.00");

        SubjectGrade sixteenOfFifty = strategy.gradeSubject(new BigDecimal("16"), 50, 17, false);
        assertThat(sixteenOfFifty.percentage()).isEqualByComparingTo("32.00");
        assertThat(sixteenOfFifty.letter()).isEqualTo("F");
        assertThat(sixteenOfFifty.point()).isEqualByComparingTo("0.00");
        assertThat(sixteenOfFifty.passed()).isFalse();
    }

    @Test
    void gradeSubject_belowPassMarks_isFailEvenIfPercentWouldPass() {
        SubjectGrade grade = strategy.gradeSubject(new BigDecimal("39"), 100, 40, false);

        assertThat(grade.percentage()).isEqualByComparingTo("39.00");
        assertThat(grade.letter()).isEqualTo("F");
        assertThat(grade.point()).isEqualByComparingTo("0.00");
        assertThat(grade.passed()).isFalse();
        assertThat(grade.absent()).isFalse();
    }

    @Test
    void gradeSubject_absent_isFailWithNoMarks() {
        SubjectGrade grade = strategy.gradeSubject(new BigDecimal("90"), 100, 33, true);

        assertThat(grade.absent()).isTrue();
        assertThat(grade.passed()).isFalse();
        assertThat(grade.obtained()).isNull();
        assertThat(grade.percentage()).isEqualByComparingTo("0.00");
        assertThat(grade.letter()).isEqualTo("F");
        assertThat(grade.point()).isEqualByComparingTo("0.00");
    }

    @Test
    void summarize_allPassed_averagesThePoints() {
        List<SubjectGrade> grades = List.of(
                strategy.gradeSubject(new BigDecimal("85"), 100, 33, false), // 5.00
                strategy.gradeSubject(new BigDecimal("72"), 100, 33, false), // 4.00
                strategy.gradeSubject(new BigDecimal("63"), 100, 33, false)  // 3.50
        );

        ResultSummary summary = strategy.summarize(grades);

        assertThat(summary.totalObtained()).isEqualByComparingTo("220");
        assertThat(summary.totalFullMarks()).isEqualTo(300);
        assertThat(summary.percentage()).isEqualByComparingTo("73.33");
        assertThat(summary.gpa()).isEqualByComparingTo("4.17"); // 12.50 / 3 = 4.166... -> 4.17 HALF_UP
        assertThat(summary.letter()).isEqualTo("A");
        assertThat(summary.passed()).isTrue();
    }

    @Test
    void summarize_oneFailedSubject_makesGpaZero() {
        List<SubjectGrade> grades = List.of(
                strategy.gradeSubject(new BigDecimal("92"), 100, 33, false), // A+ 5.00
                strategy.gradeSubject(new BigDecimal("75"), 100, 33, false), // A  4.00
                strategy.gradeSubject(new BigDecimal("25"), 100, 33, false)  // F  0.00
        );

        ResultSummary summary = strategy.summarize(grades);

        assertThat(summary.totalObtained()).isEqualByComparingTo("192");
        assertThat(summary.totalFullMarks()).isEqualTo(300);
        assertThat(summary.percentage()).isEqualByComparingTo("64.00");
        assertThat(summary.gpa()).isEqualByComparingTo("0.00");
        assertThat(summary.letter()).isEqualTo("F");
        assertThat(summary.passed()).isFalse();
    }

    @Test
    void summarize_noSubjects_isNotAPass() {
        ResultSummary summary = strategy.summarize(List.of());

        assertThat(summary.totalObtained()).isEqualByComparingTo("0");
        assertThat(summary.totalFullMarks()).isZero();
        assertThat(summary.percentage()).isEqualByComparingTo("0.00");
        assertThat(summary.gpa()).isEqualByComparingTo("0.00");
        assertThat(summary.letter()).isEqualTo("F");
        assertThat(summary.passed()).isFalse();
    }
}
