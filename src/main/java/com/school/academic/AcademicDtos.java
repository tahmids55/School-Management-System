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

    /** classTeacherId is accepted but not stored yet: F16 maps the class teacher. */
    public record ClassRequest(
            @NotNull Long academicYearId,
            @NotBlank @Size(max = 30) String name,
            @NotBlank @Size(max = 10) String section,
            @Size(max = 20) String room,
            @NotNull @Min(1) @Max(200) Integer capacity,
            Long classTeacherId
    ) {
    }

    /**
     * classTeacherId / classTeacherName stay null until F16 (and are then left out of the JSON);
     * studentCount comes from the students table. The JSON shape never has to change.
     */
    public record ClassResponse(
            Long id,
            Long academicYearId,
            String academicYearName,
            String name,
            String section,
            String displayName,
            String room,
            int capacity,
            Long classTeacherId,
            String classTeacherName,
            long studentCount
    ) {
        /** The class's academicYear must already be loaded (join fetch), or this triggers a query. */
        public static ClassResponse from(SchoolClass schoolClass, long studentCount) {
            return new ClassResponse(
                    schoolClass.getId(),
                    schoolClass.getAcademicYear().getId(),
                    schoolClass.getAcademicYear().getName(),
                    schoolClass.getName(),
                    schoolClass.getSection(),
                    schoolClass.getDisplayName(),
                    schoolClass.getRoom(),
                    schoolClass.getCapacity(),
                    null,
                    null,
                    studentCount
            );
        }
    }
}
