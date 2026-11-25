package com.school.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Page<AppUser> findByRole(Role role, Pageable pageable);

    @Query("""
            select u from AppUser u
            where lower(u.username) like :pattern escape '!'
               or lower(u.fullName) like :pattern escape '!'
               or lower(u.email) like :pattern escape '!'
            """)
    Page<AppUser> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select u from AppUser u
            where u.role = :role
              and (lower(u.username) like :pattern escape '!'
                or lower(u.fullName) like :pattern escape '!'
                or lower(u.email) like :pattern escape '!')
            """)
    Page<AppUser> searchByRole(@Param("role") Role role, @Param("pattern") String pattern, Pageable pageable);
}
