package com.school.grading;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strategy contract for converting subject marks into grades and summarizing an overall result.
 */
public interface GradingStrategy {

    String code();

    String displayName();

    SubjectGrade gradeSubject(BigDecimal obtained, int fullMarks, int passMarks, boolean absent);

    ResultSummary summarize(List<SubjectGrade> grades);
}
