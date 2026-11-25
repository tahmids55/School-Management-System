package com.school.teacher;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    @Query(
            value = "select t from Teacher t left join fetch t.user",
            countQuery = "select count(t) from Teacher t"
    )
    Page<Teacher> findAllWithUser(Pageable pageable);

    @Query(
            value = "select t from Teacher t left join fetch t.user where t.status = :status",
            countQuery = "select count(t) from Teacher t where t.status = :status"
    )
    Page<Teacher> findByStatusWithUser(@Param("status") TeacherStatus status, Pageable pageable);

    @Query(
            value = """
                    select t from Teacher t
                    left join fetch t.user
                    where (lower(concat(concat(t.firstName, ' '), t.lastName)) like :pattern escape '!'
                        or lower(t.employeeNo) like :pattern escape '!'
                        or lower(t.email) like :pattern escape '!')
                    """,
            countQuery = """
                    select count(t) from Teacher t
                    where (lower(concat(concat(t.firstName, ' '), t.lastName)) like :pattern escape '!'
                        or lower(t.employeeNo) like :pattern escape '!'
                        or lower(t.email) like :pattern escape '!')
                    """
    )
    Page<Teacher> searchWithUser(@Param("pattern") String pattern, Pageable pageable);

    @Query(
            value = """
                    select t from Teacher t
                    left join fetch t.user
                    where t.status = :status
                      and (lower(concat(concat(t.firstName, ' '), t.lastName)) like :pattern escape '!'
                        or lower(t.employeeNo) like :pattern escape '!'
                        or lower(t.email) like :pattern escape '!')
                    """,
            countQuery = """
                    select count(t) from Teacher t
                    where t.status = :status
                      and (lower(concat(concat(t.firstName, ' '), t.lastName)) like :pattern escape '!'
                        or lower(t.employeeNo) like :pattern escape '!'
                        or lower(t.email) like :pattern escape '!')
                    """
    )
    Page<Teacher> searchByStatusWithUser(
            @Param("status") TeacherStatus status,
            @Param("pattern") String pattern,
            Pageable pageable
    );

    @Query("select t from Teacher t left join fetch t.user where t.id = :id")
    Optional<Teacher> findByIdWithUser(@Param("id") Long id);

    @Query("select t.id from Teacher t join t.user u where lower(u.username) = lower(:username)")
    Optional<Long> findIdByUserUsernameIgnoreCase(@Param("username") String username);

    @Query("select t from Teacher t join t.user u where lower(u.username) = lower(:username)")
    Optional<Teacher> findByUserUsernameIgnoreCase(@Param("username") String username);

    boolean existsByEmployeeNoIgnoreCase(String employeeNo);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
