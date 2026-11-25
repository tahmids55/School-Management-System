package com.school.grading;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradingStrategiesTest {

    private final Gpa4GradingStrategy gpa4 = new Gpa4GradingStrategy();
    private final Gpa5GradingStrategy gpa5 = new Gpa5GradingStrategy();

    @ParameterizedTest
    @CsvSource({
            "100, A, 4.00, true",
            "90,  A, 4.00, true",
            "89,  B, 3.00, true",
            "80,  B, 3.00, true",
            "79,  C, 2.00, true",
            "70,  C, 2.00, true",
            "69,  D, 1.00, true",
            "60,  D, 1.00, true",
            "59,  F, 0.00, false",
            "0,   F, 0.00, false"
    })
    void gpa4_bands(String marks, String expectedLetter, String expectedPoint, boolean expectedPassed) {
        SubjectGrade grade = gpa4.gradeSubject(new BigDecimal(marks), 100, 60, false);

        assertThat(grade.letter()).isEqualTo(expectedLetter);
        assertThat(grade.point()).isEqualByComparingTo(expectedPoint);
        assertThat(grade.passed()).isEqualTo(expectedPassed);
        assertThat(grade.percentage()).isEqualByComparingTo(new BigDecimal(marks));
    }

    @Test
    void gpa4_failedSubjectCountsAsZeroButDoesNotZeroTheGpa() {
        List<SubjectGrade> grades = List.of(
                gpa4.gradeSubject(new BigDecimal("95"), 100, 60, false), // A 4.00
                gpa4.gradeSubject(new BigDecimal("82"), 100, 60, false), // B 3.00
                gpa4.gradeSubject(new BigDecimal("50"), 100, 60, false)  // F 0.00
        );

        ResultSummary summary = gpa4.summarize(grades);

        assertThat(summary.gpa()).isEqualByComparingTo("2.33"); // (4 + 3 + 0) / 3 = 2.333... -> 2.33
        assertThat(summary.letter()).isEqualTo("C"); // >= 1.5 and < 2.5
        assertThat(summary.passed()).isFalse();

        // Empty list is also not passed
        assertThat(gpa4.summarize(List.of()).passed()).isFalse();
    }

    @Test
    void gradeScale_isASingleton() throws Exception {
        BangladeshGradeScale first = BangladeshGradeScale.getInstance();
        BangladeshGradeScale second = BangladeshGradeScale.getInstance();

        assertThat(first).isSameAs(second);
        assertThat(Modifier.isPrivate(BangladeshGradeScale.class.getDeclaredConstructor().getModifiers())).isTrue();
        assertThatThrownBy(() -> first.bands().add(new GradeBand(99, "X", BigDecimal.TEN)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "5.00, A+",
            "4.99, A",
            "4.00, A",
            "3.99, A-",
            "3.50, A-",
            "3.49, B",
            "3.00, B",
            "2.99, C",
            "2.00, C",
            "1.99, D",
            "1.00, D",
            "0.99, F",
            "0.00, F"
    })
    void gradeScale_letterForGpa(String gpa, String expectedLetter) {
        assertThat(BangladeshGradeScale.getInstance().letterForGpa(new BigDecimal(gpa)))
                .isEqualTo(expectedLetter);
    }

    @Test
    void resolver_picksTheConfiguredStrategy() {
        GradingStrategyResolver defaultResolver = new GradingStrategyResolver(List.of(gpa5, gpa4), "GPA_5");
        assertThat(defaultResolver.active().code()).isEqualTo("GPA_5");
        assertThat(defaultResolver.active().displayName()).isEqualTo("GPA 5.0 (Bangladesh)");

        GradingStrategyResolver gpa4Resolver = new GradingStrategyResolver(List.of(gpa5, gpa4), "GPA_4");
        assertThat(gpa4Resolver.active().code()).isEqualTo("GPA_4");
        assertThat(gpa4Resolver.active().displayName()).isEqualTo("GPA 4.0 (International)");

        new ApplicationContextRunner()
                .withUserConfiguration(Gpa5GradingStrategy.class, Gpa4GradingStrategy.class, GradingStrategyResolver.class)
                .withPropertyValues("app.grading.strategy=GPA_4")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GradingStrategyResolver.class).active().code()).isEqualTo("GPA_4");
                });
    }

    @Test
    void resolver_unknownStrategy_failsAtStartup() {
        assertThatThrownBy(() -> new GradingStrategyResolver(List.of(gpa5, gpa4), "PERCENTAGE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown grading strategy 'PERCENTAGE'. Use one of [GPA_5, GPA_4]");

        new ApplicationContextRunner()
                .withUserConfiguration(Gpa5GradingStrategy.class, Gpa4GradingStrategy.class, GradingStrategyResolver.class)
                .withPropertyValues("app.grading.strategy=UNKNOWN")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Unknown grading strategy 'UNKNOWN'. Use one of [GPA_5, GPA_4]");
                });
    }
}
