package com.school.exam;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

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
}
