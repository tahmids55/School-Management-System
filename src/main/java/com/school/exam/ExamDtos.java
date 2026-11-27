package com.school.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ExamDtos {

    private ExamDtos() {
    }

    public record ExamRequest(
            @NotNull Long academicYearId,
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {
    }

    public record ExamResponse(
            Long id,
            Long academicYearId,
            String academicYearName,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            ExamStatus status,
            Instant publishedAt
    ) {
        public static ExamResponse from(Exam exam) {
            return new ExamResponse(
                    exam.getId(),
                    exam.getAcademicYear().getId(),
                    exam.getAcademicYear().getName(),
                    exam.getName(),
                    exam.getStartDate(),
                    exam.getEndDate(),
                    exam.getStatus(),
                    exam.getPublishedAt()
            );
        }
    }

    public record MarkRow(
            Long studentId,
            Integer rollNo,
            String admissionNo,
            String studentName,
            BigDecimal marksObtained,
            boolean absent
    ) {
    }

    public record MarkSheet(
            Long examId,
            String examName,
            Long classId,
            String className,
            Long subjectId,
            String subjectCode,
            String subjectName,
            int fullMarks,
            int passMarks,
            boolean editable,
            List<MarkRow> rows
    ) {
    }

    public record MarkEntry(
            @NotNull Long studentId,
            @DecimalMin("0.0") @DecimalMax("1000.0") BigDecimal marksObtained,
            boolean absent
    ) {
    }

    public record MarkSheetRequest(
            @NotNull Long classId,
            @NotNull Long subjectId,
            @NotEmpty @Valid List<MarkEntry> entries
    ) {
    }

    public record SubjectResult(
            Long subjectId,
            String subjectCode,
            String subjectName,
            int fullMarks,
            int passMarks,
            BigDecimal obtained,
            boolean absent,
            String letter,
            BigDecimal point,
            boolean passed
    ) {
    }

    public record StudentResultRow(
            Long studentId,
            Integer rollNo,
            String admissionNo,
            String studentName,
            BigDecimal totalObtained,
            int totalFullMarks,
            BigDecimal percentage,
            BigDecimal gpa,
            String letter,
            boolean passed,
            Integer position,
            List<SubjectResult> subjects
    ) {
    }

    public record ClassResult(
            Long examId,
            String examName,
            ExamStatus status,
            Long classId,
            String className,
            String gradingScheme,
            int passed,
            int failed,
            List<StudentResultRow> students
    ) {
    }

    public record MyExamResult(
            Long examId,
            String examName,
            LocalDate endDate,
            BigDecimal gpa,
            String letter,
            boolean passed,
            Integer position
    ) {
    }
}
