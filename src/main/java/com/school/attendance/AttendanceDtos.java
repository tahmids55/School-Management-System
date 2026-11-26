package com.school.attendance;

import com.school.student.Student;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Request and response records of the attendance API. JSON leaves out null fields. */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /** One line of the sheet. status and remarks are null when the student has no record that day. */
    public record SheetRow(
            Long studentId,
            Integer rollNo,
            String admissionNo,
            String studentName,
            AttendanceStatus status,
            String remarks
    ) {
        static SheetRow from(Student student, AttendanceRecord record) {
            return new SheetRow(
                    student.getId(),
                    student.getRollNo(),
                    student.getAdmissionNo(),
                    student.getFullName(),
                    record != null ? record.getStatus() : null,
                    record != null ? record.getRemarks() : null
            );
        }
    }

    public record AttendanceSheet(
            Long classId,
            String className,
            LocalDate date,
            boolean taken,
            List<SheetRow> rows
    ) {
    }

    public record Entry(
            @NotNull Long studentId,
            @NotNull AttendanceStatus status,
            @Size(max = 255) String remarks
    ) {
    }

    public record SaveSheetRequest(
            @NotNull Long classId,
            @NotNull LocalDate date,
            @NotEmpty @Valid List<Entry> entries
    ) {
    }
}
