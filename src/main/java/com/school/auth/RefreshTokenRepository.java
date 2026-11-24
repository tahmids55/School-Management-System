package com.school.auth;

import com.school.user.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt join fetch rt.user where rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken rt where rt.user = :user")
    int deleteAllByUser(@Param("user") AppUser user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken rt where rt.user = :user and rt.revokedAt is not null")
    int deleteRotatedByUser(@Param("user") AppUser user);
}
