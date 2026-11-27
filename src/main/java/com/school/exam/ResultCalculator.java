package com.school.exam;

import com.school.academic.Subject;
import com.school.exam.ExamDtos.StudentResultRow;
import com.school.exam.ExamDtos.SubjectResult;
import com.school.grading.GradingStrategy;
import com.school.grading.ResultSummary;
import com.school.grading.SubjectGrade;
import com.school.student.Student;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, database-free calculator that grades students and ranks a class merit list.
 */
@Component
public class ResultCalculator {

    private static final Comparator<StudentResultRow> PASSED_ORDER = Comparator
            .comparing(StudentResultRow::gpa, Comparator.reverseOrder())
            .thenComparing(StudentResultRow::percentage, Comparator.reverseOrder())
            .thenComparing(StudentResultRow::rollNo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StudentResultRow::studentId);

    private static final Comparator<StudentResultRow> FAILED_ORDER = Comparator
            .comparing(StudentResultRow::percentage, Comparator.reverseOrder())
            .thenComparing(StudentResultRow::rollNo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StudentResultRow::studentId);

    public List<StudentResultRow> calculateAndRank(
            List<Student> students,
            List<Subject> subjects,
            List<Mark> marks,
            GradingStrategy strategy
    ) {
        Map<Long, Map<Long, Mark>> marksByStudentAndSubject = new HashMap<>();
        for (Mark mark : marks) {
            marksByStudentAndSubject
                    .computeIfAbsent(mark.getStudent().getId(), ignored -> new HashMap<>())
                    .put(mark.getSubject().getId(), mark);
        }

        List<StudentResultRow> unranked = new ArrayList<>(students.size());
        for (Student student : students) {
            Map<Long, Mark> studentMarks = marksByStudentAndSubject.getOrDefault(student.getId(), Map.of());
            unranked.add(calculateStudent(student, subjects, studentMarks, strategy));
        }

        return rank(unranked);
    }

    public StudentResultRow calculateStudent(
            Student student,
            List<Subject> subjects,
            Map<Long, Mark> marksBySubjectId,
            GradingStrategy strategy
    ) {
        List<SubjectGrade> grades = new ArrayList<>(subjects.size());
        List<SubjectResult> subjectResults = new ArrayList<>(subjects.size());

        for (Subject subject : subjects) {
            Mark mark = marksBySubjectId.get(subject.getId());
            boolean absent = (mark == null) || mark.isAbsent() || mark.getMarksObtained() == null;
            BigDecimal obtained = absent ? null : mark.getMarksObtained();

            SubjectGrade grade = strategy.gradeSubject(
                    obtained,
                    subject.getFullMarks(),
                    subject.getPassMarks(),
                    absent
            );
            grades.add(grade);
            subjectResults.add(new SubjectResult(
                    subject.getId(),
                    subject.getCode(),
                    subject.getName(),
                    subject.getFullMarks(),
                    subject.getPassMarks(),
                    grade.obtained(),
                    grade.absent(),
                    grade.letter(),
                    grade.point(),
                    grade.passed()
            ));
        }

        ResultSummary summary = strategy.summarize(grades);
        return new StudentResultRow(
                student.getId(),
                student.getRollNo(),
                student.getAdmissionNo(),
                student.getFullName(),
                summary.totalObtained(),
                summary.totalFullMarks(),
                summary.percentage(),
                summary.gpa(),
                summary.letter(),
                summary.passed(),
                null,
                subjectResults
        );
    }

    public List<StudentResultRow> rank(List<StudentResultRow> rows) {
        List<StudentResultRow> passedRows = new ArrayList<>();
        List<StudentResultRow> failedRows = new ArrayList<>();
        for (StudentResultRow row : rows) {
            if (row.passed()) {
                passedRows.add(row);
            } else {
                failedRows.add(row);
            }
        }

        passedRows.sort(PASSED_ORDER);
        failedRows.sort(FAILED_ORDER);

        List<StudentResultRow> ranked = new ArrayList<>(rows.size());
        int currentPosition = 0;
        StudentResultRow previous = null;
        for (int i = 0; i < passedRows.size(); i++) {
            StudentResultRow row = passedRows.get(i);
            boolean tiedWithPrevious = previous != null
                    && row.gpa().compareTo(previous.gpa()) == 0
                    && row.percentage().compareTo(previous.percentage()) == 0;
            if (!tiedWithPrevious) {
                currentPosition = i + 1;
            }
            ranked.add(withPosition(row, currentPosition));
            previous = row;
        }

        for (StudentResultRow row : failedRows) {
            ranked.add(withPosition(row, null));
        }

        return ranked;
    }

    private static StudentResultRow withPosition(StudentResultRow row, Integer position) {
        return new StudentResultRow(
                row.studentId(),
                row.rollNo(),
                row.admissionNo(),
                row.studentName(),
                row.totalObtained(),
                row.totalFullMarks(),
                row.percentage(),
                row.gpa(),
                row.letter(),
                row.passed(),
                position,
                row.subjects()
        );
    }
}
