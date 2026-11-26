package com.school.attendance;

/** The four values allowed by the CHECK constraint on attendance_records.status. */
public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    EXCUSED
}
