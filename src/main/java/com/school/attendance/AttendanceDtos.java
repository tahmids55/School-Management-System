package com.school.attendance;

import com.school.student.Student;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    // ---------- Reports (F20) ----------

    /** One student's line of the class report. */
    public record StudentAttendance(
            Long studentId,
            Integer rollNo,
            String studentName,
            long present,
            long absent,
            long late,
            long excused,
            BigDecimal percentage,
            boolean low
    ) {
        /** counts = how often each status occurs; a missing status counts as 0. */
        static StudentAttendance from(Student student, Map<AttendanceStatus, Long> counts, int lowThreshold) {
            long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
            long absent = counts.getOrDefault(AttendanceStatus.ABSENT, 0L);
            long late = counts.getOrDefault(AttendanceStatus.LATE, 0L);
            long excused = counts.getOrDefault(AttendanceStatus.EXCUSED, 0L);
            BigDecimal percentage = AttendanceMath.percentage(present, late, absent);
            return new StudentAttendance(
                    student.getId(), student.getRollNo(), student.getFullName(),
                    present, absent, late, excused,
                    percentage, AttendanceMath.isLow(percentage, lowThreshold));
        }
    }

    public record ClassReport(
            Long classId,
            String className,
            LocalDate from,
            LocalDate to,
            long daysTaken,
            int lowThreshold,
            List<StudentAttendance> students
    ) {
    }

    public record DayRecord(
            LocalDate date,
            AttendanceStatus status,
            String remarks
    ) {
        static DayRecord from(AttendanceRecord record) {
            return new DayRecord(record.getAttendanceDate(), record.getStatus(), record.getRemarks());
        }
    }

    public record StudentSummary(
            Long studentId,
            String studentName,
            LocalDate from,
            LocalDate to,
            long present,
            long absent,
            long late,
            long excused,
            BigDecimal percentage,
            boolean low,
            List<DayRecord> records
    ) {
        /** records = the student's days in the range, already newest first. */
        static StudentSummary from(
                Student student,
                LocalDate from,
                LocalDate to,
                List<AttendanceRecord> records,
                int lowThreshold
        ) {
            Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
            for (AttendanceRecord record : records) {
                counts.merge(record.getStatus(), 1L, Long::sum);
            }
            StudentAttendance totals = StudentAttendance.from(student, counts, lowThreshold);
            return new StudentSummary(
                    student.getId(), student.getFullName(), from, to,
                    totals.present(), totals.absent(), totals.late(), totals.excused(),
                    totals.percentage(), totals.low(),
                    records.stream().map(DayRecord::from).toList());
        }
    }
}
