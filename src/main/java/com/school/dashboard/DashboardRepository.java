package com.school.dashboard;

import com.school.exam.Exam;
import com.school.fee.Payment;
import com.school.student.Student;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The read-only, GROUPED queries of the dashboards, kept in one place.
 * Each method is ONE SQL statement whatever the number of rows: the database counts and sums,
 * so no query runs per student, class or day (no N+1).
 * It extends the marker {@link Repository} (not JpaRepository), so it has no save or delete methods.
 */
public interface DashboardRepository extends Repository<Student, Long> {

    @Query("select count(s) from Student s where s.status = com.school.student.StudentStatus.ACTIVE")
    long countActiveStudents();

    @Query("select count(t) from Teacher t where t.status = com.school.teacher.TeacherStatus.ACTIVE")
    long countActiveTeachers();

    /** Rows of [gender, count] for active students. */
    @Query("""
            select s.gender, count(s) from Student s
            where s.status = com.school.student.StudentStatus.ACTIVE
            group by s.gender
            """)
    List<Object[]> countActiveStudentsByGender();

    /** Rows of [date, status, count] for the whole school in the range: at most 4 rows per day. */
    @Query("""
            select r.attendanceDate, r.status, count(r) from AttendanceRecord r
            where r.attendanceDate between :from and :to
            group by r.attendanceDate, r.status
            """)
    List<Object[]> countAttendanceByDateAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Which of the given classes have attendance saved on that day. */
    @Query("""
            select distinct r.schoolClass.id from AttendanceRecord r
            where r.attendanceDate = :date and r.schoolClass.id in :classIds
            """)
    Set<Long> findClassIdsWithAttendanceOn(@Param("date") LocalDate date, @Param("classIds") Collection<Long> classIds);

    /**
     * Rows of [month "YYYY-MM", sum] of payments since {@code from}.
     * The month is taken in the school's time zone, so a payment at 00:30 on 1 Dec in Dhaka
     * (still 30 Nov in UTC) counts for December.
     */
    @Query(value = """
            SELECT to_char(paid_at AT TIME ZONE CAST(:zone AS text), 'YYYY-MM') AS month, SUM(amount)
            FROM payments
            WHERE paid_at >= :from
            GROUP BY month
            """, nativeQuery = true)
    List<Object[]> sumPaymentsByMonth(@Param("from") Instant from, @Param("zone") String zone);

    /** Newest payments with their invoice and student in the same select. */
    @Query("""
            select p from Payment p
            join fetch p.invoice i
            join fetch i.student
            order by p.paidAt desc, p.id desc
            """)
    List<Payment> findRecentPayments(Pageable pageable);

    /** The most recently published exam (the page size decides how many). */
    @Query("""
            select e from Exam e
            where e.status = com.school.exam.ExamStatus.PUBLISHED
            order by e.publishedAt desc, e.id desc
            """)
    List<Exam> findLatestPublishedExams(Pageable pageable);

    /** Rows of [studentId, marksObtained, absent, fullMarks, passMarks]: every mark of one exam. */
    @Query("""
            select m.student.id, m.marksObtained, m.absent, s.fullMarks, s.passMarks
            from Mark m join m.subject s
            where m.exam.id = :examId
            """)
    List<Object[]> findMarksOfExam(@Param("examId") Long examId);
}
