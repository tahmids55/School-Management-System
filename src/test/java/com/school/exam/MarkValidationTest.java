package com.school.exam;

import com.school.academic.Subject;
import com.school.common.exception.BusinessRuleException;
import com.school.exam.ExamDtos.MarkEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkValidationTest {

    private final Subject math = new Subject("MATH", "Mathematics", 100, 33);

    @Test
    void validMarks_areAccepted() {
        assertThat(MarkValidator.validateEntry(new MarkEntry(1L, BigDecimal.ZERO, false), math))
                .isEqualByComparingTo("0");
        assertThat(MarkValidator.validateEntry(new MarkEntry(1L, new BigDecimal("78.50"), false), math))
                .isEqualByComparingTo("78.50");
        assertThat(MarkValidator.validateEntry(new MarkEntry(1L, new BigDecimal("100"), false), math))
                .isEqualByComparingTo("100");
    }

    @Test
    void marksOutsideZeroToFullMarks_areRejected() {
        assertThatThrownBy(() -> MarkValidator.validateEntry(new MarkEntry(1L, new BigDecimal("-0.01"), false), math))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Marks must be between 0 and 100 for MATH");

        assertThatThrownBy(() -> MarkValidator.validateEntry(new MarkEntry(1L, new BigDecimal("100.01"), false), math))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Marks must be between 0 and 100 for MATH");

        assertThatThrownBy(() -> MarkValidator.validateEntry(new MarkEntry(1L, new BigDecimal("50.125"), false), math))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Marks can have at most 2 decimal places");
    }

    @Test
    void absentStudent_needsNoMarks() {
        assertThat(MarkValidator.validateEntry(new MarkEntry(2L, null, true), math)).isNull();
        assertThat(MarkValidator.validateEntry(new MarkEntry(2L, new BigDecimal("99"), true), math)).isNull();
    }

    @Test
    void presentStudentWithoutMarks_isRejected() {
        assertThatThrownBy(() -> MarkValidator.validateEntry(new MarkEntry(3L, null, false), math))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Enter marks or mark the student absent (student 3)");
    }
}
