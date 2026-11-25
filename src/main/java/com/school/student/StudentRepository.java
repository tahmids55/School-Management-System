package com.school.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    /**
     * Search page. The entity graph loads each student's class in the SAME select (a join),
     * so reading the class name later does not fire one query per row (N+1).
     * Spring Data runs a separate plain COUNT query for the total.
     */
    @Override
    @EntityGraph(attributePaths = "schoolClass")
    Page<Student> findAll(Specification<Student> spec, Pageable pageable);

    /** Same, without paging: used by the CSV export. */
    @Override
    @EntityGraph(attributePaths = "schoolClass")
    List<Student> findAll(Specification<Student> spec, Sort sort);

    @Query("select s from Student s left join fetch s.schoolClass left join fetch s.user where s.id = :id")
    Optional<Student> findByIdWithClassAndUser(@Param("id") Long id);

    /** The active students of one class, ordered by roll: the class roster. */
    @Query("select s from Student s join fetch s.schoolClass"
            + " where s.schoolClass.id = :classId and s.status = com.school.student.StudentStatus.ACTIVE"
            + " order by s.rollNo, s.admissionNo")
    List<Student> findActiveRoster(@Param("classId") Long classId);

    boolean existsByAdmissionNo(String admissionNo);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsBySchoolClassIdAndRollNo(Long classId, Integer rollNo);

    boolean existsBySchoolClassIdAndRollNoAndIdNot(Long classId, Integer rollNo, Long id);

    long countBySchoolClassIdAndStatus(Long classId, StudentStatus status);

    /** Highest roll in a class (any status, because rolls stay unique for every row); null when empty. */
    @Query("select max(s.rollNo) from Student s where s.schoolClass.id = :classId")
    Integer findMaxRollNo(@Param("classId") Long classId);

    /** Student profile id of a login, for /auth/me. Usernames are stored lower-case. */
    @Query("select s.id from Student s where s.user.username = lower(:username)")
    Optional<Long> findIdByUsername(@Param("username") String username);
}
