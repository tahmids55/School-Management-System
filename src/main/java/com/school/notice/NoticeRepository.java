package com.school.notice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findByAudienceIn(Collection<NoticeAudience> audiences, Pageable pageable);

    Optional<Notice> findByIdAndAudienceIn(Long id, Collection<NoticeAudience> audiences);
}
