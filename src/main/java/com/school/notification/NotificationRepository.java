package com.school.notification;

import com.school.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** One user's notifications only. Uses index idx_notifications_user_unread. */
    @Query(value = "select n from Notification n where n.user.id = :userId",
            countQuery = "select count(n) from Notification n where n.user.id = :userId")
    Page<Notification> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("select count(n) from Notification n where n.user.id = :userId and n.readAt is null")
    long countUnread(@Param("userId") Long userId);

    /** Empty when the notification does not exist OR belongs to someone else (both become 404). */
    @Query("select n from Notification n where n.id = :id and n.user.id = :userId")
    Optional<Notification> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** "Read all" in ONE update statement instead of loading every row. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    // ---------- recipients (read-only, one query each) ----------

    /** Enabled logins with one of the roles: who receives a notice. */
    @Query("select u.id from AppUser u where u.enabled = true and u.role in :roles order by u.id")
    List<Long> findEnabledUserIdsByRoles(@Param("roles") Collection<Role> roles);

    /** Every ACTIVE student in a class of the exam's academic year, with their login (if any) and guardian phone. */
    @Query("""
            select new com.school.notification.Recipient(u.id, s.firstName, s.guardianPhone)
            from Student s
            join s.schoolClass c
            left join s.user u
            where s.status = com.school.student.StudentStatus.ACTIVE
              and c.academicYear.id = (select e.academicYear.id from Exam e where e.id = :examId)
            order by s.id
            """)
    List<Recipient> findExamRecipients(@Param("examId") Long examId);

    @Query("""
            select new com.school.notification.Recipient(u.id, s.firstName, s.guardianPhone)
            from Student s
            left join s.user u
            where s.id = :studentId
            """)
    Optional<Recipient> findStudentRecipient(@Param("studentId") Long studentId);
}
