package com.school.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /**
     * The records of the given students on one day, in ONE query (no N+1).
     * Looked up by student (not by class) because the unique key is (student, date).
     */
    @Query("""
            select r from AttendanceRecord r
            where r.attendanceDate = :date
              and r.student.id in :studentIds
            """)
    List<AttendanceRecord> findByDateAndStudentIds(
            @Param("date") LocalDate date,
            @Param("studentIds") Collection<Long> studentIds
    );

    /** True when attendance of this class was saved for that day. */
    boolean existsBySchoolClassIdAndAttendanceDate(Long schoolClassId, LocalDate attendanceDate);
}
