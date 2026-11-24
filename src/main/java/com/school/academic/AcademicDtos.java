package com.school.academic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class AcademicDtos {

    private AcademicDtos() {
    }

    public record AcademicYearRequest(
            @NotBlank @Size(max = 20) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {
    }

    public record AcademicYearResponse(
            Long id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean current
    ) {
        public static AcademicYearResponse from(AcademicYear year) {
            return new AcademicYearResponse(
                    year.getId(),
                    year.getName(),
                    year.getStartDate(),
                    year.getEndDate(),
                    year.isCurrent()
            );
        }
    }

    public record SubjectRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(1) @Max(1000) Integer fullMarks,
            @NotNull @Min(0) @Max(1000) Integer passMarks
    ) {
    }

    public record SubjectResponse(
            Long id,
            String code,
            String name,
            int fullMarks,
            int passMarks
    ) {
        public static SubjectResponse from(Subject subject) {
            return new SubjectResponse(
                    subject.getId(),
                    subject.getCode(),
                    subject.getName(),
                    subject.getFullMarks(),
                    subject.getPassMarks()
            );
        }
    }
}
