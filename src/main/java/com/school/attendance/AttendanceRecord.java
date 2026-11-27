package com.school.attendance;

import com.school.academic.SchoolClass;
import com.school.common.BaseEntity;
import com.school.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

/**
 * One student's attendance on one day, mapped to {@code attendance_records}.
 * At most one row per student and day (uq_attendance_per_day), so saving a day again updates the row.
 */
@Getter
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    /** The class the student was in when attendance was taken. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceStatus status;

    @Column(length = 255)
    private String remarks;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(
            Student student,
            SchoolClass schoolClass,
            LocalDate attendanceDate,
            AttendanceStatus status,
            String remarks
    ) {
        this.student = student;
        this.schoolClass = schoolClass;
        this.attendanceDate = attendanceDate;
        this.status = status;
        this.remarks = remarks;
    }

    /** Correcting a saved day: the student and date stay, the rest is replaced. */
    public void correct(SchoolClass schoolClass, AttendanceStatus status, String remarks) {
        this.schoolClass = schoolClass;
        this.status = status;
        this.remarks = remarks;
    }
}
