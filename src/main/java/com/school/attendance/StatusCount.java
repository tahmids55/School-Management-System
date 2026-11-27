package com.school.attendance;

/** One row of the grouped count query: "student 5 was LATE 3 times". Never leaves the service. */
public record StatusCount(Long studentId, AttendanceStatus status, Long count) {
}
