package com.school.academic;

import com.school.teacher.Teacher;
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

    public record ClassRequest(
            @NotNull Long academicYearId,
            @NotBlank @Size(max = 30) String name,
            @NotBlank @Size(max = 10) String section,
            @Size(max = 20) String room,
            @NotNull @Min(1) @Max(200) Integer capacity,
            Long classTeacherId
    ) {
    }

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
        /** The class's academicYear and optional classTeacher must already be loaded (join fetch) to avoid N+1. */
        public static ClassResponse from(SchoolClass schoolClass, long studentCount) {
            Teacher classTeacher = schoolClass.getClassTeacher();
            return new ClassResponse(
                    schoolClass.getId(),
                    schoolClass.getAcademicYear().getId(),
                    schoolClass.getAcademicYear().getName(),
                    schoolClass.getName(),
                    schoolClass.getSection(),
                    schoolClass.getDisplayName(),
                    schoolClass.getRoom(),
                    schoolClass.getCapacity(),
                    classTeacher != null ? classTeacher.getId() : null,
                    classTeacher != null ? classTeacher.getFullName() : null,
                    studentCount
            );
        }
    }

    public record ClassSubjectRequest(
            @NotNull Long subjectId,
            Long teacherId
    ) {
    }

    public record ClassSubjectResponse(
            Long id,
            Long subjectId,
            String subjectCode,
            String subjectName,
            int fullMarks,
            int passMarks,
            Long teacherId,
            String teacherName
    ) {
        public static ClassSubjectResponse from(ClassSubject classSubject) {
            Subject subject = classSubject.getSubject();
            Teacher teacher = classSubject.getTeacher();
            return new ClassSubjectResponse(
                    classSubject.getId(),
                    subject.getId(),
                    subject.getCode(),
                    subject.getName(),
                    subject.getFullMarks(),
                    subject.getPassMarks(),
                    teacher != null ? teacher.getId() : null,
                    teacher != null ? teacher.getFullName() : null
            );
        }
    }
}
