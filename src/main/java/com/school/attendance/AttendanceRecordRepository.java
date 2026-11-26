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

    /**
     * Counts per student and status for a class and date range, in ONE grouped query.
     * The database does the counting; at most 4 rows per student come back, not one per day.
     */
    @Query("""
            select new com.school.attendance.StatusCount(
                       r.student.id, r.status, count(r))
            from AttendanceRecord r
            where r.schoolClass.id = :classId
              and r.attendanceDate between :from and :to
            group by r.student.id, r.status
            """)
    List<StatusCount> countByStudentAndStatus(
            @Param("classId") Long classId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /** On how many different days attendance was taken for the class in the range. */
    @Query("""
            select count(distinct r.attendanceDate)
            from AttendanceRecord r
            where r.schoolClass.id = :classId
              and r.attendanceDate between :from and :to
            """)
    long countDaysTaken(
            @Param("classId") Long classId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /** One student's days in the range, newest first. */
    List<AttendanceRecord> findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
            Long studentId,
            LocalDate from,
            LocalDate to
    );
}
