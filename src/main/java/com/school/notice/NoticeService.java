package com.school.notice;

import com.school.audit.AuditService;
import com.school.common.PageResponse;
import com.school.common.SecurityUtils;
import com.school.common.exception.NotFoundException;
import com.school.notice.NoticeDtos.NoticeRequest;
import com.school.notice.NoticeDtos.NoticeResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class NoticeService {

    public static final String AUDIT_NOTICE_PUBLISHED = "NOTICE_PUBLISHED";
    public static final String AUDIT_NOTICE_DELETED = "NOTICE_DELETED";

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("pinned"),
            Sort.Order.desc("publishedAt"),
            Sort.Order.desc("id")
    );

    private final NoticeRepository noticeRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public NoticeService(
            NoticeRepository noticeRepository,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.noticeRepository = noticeRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NoticeResponse> list(Pageable pageable) {
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);

        List<NoticeAudience> audiences = visibleAudiencesForCurrentUser();
        if (audiences.isEmpty()) {
            return PageResponse.from(Page.empty(effectivePageable));
        }

        Page<Notice> page = noticeRepository.findByAudienceIn(audiences, effectivePageable);
        return PageResponse.from(page, NoticeResponse::from);
    }

    @Transactional(readOnly = true)
    public NoticeResponse getById(Long id) {
        List<NoticeAudience> audiences = visibleAudiencesForCurrentUser();
        if (audiences.isEmpty()) {
            throw new NotFoundException("Notice", id);
        }
        Notice notice = noticeRepository.findByIdAndAudienceIn(id, audiences)
                .orElseThrow(() -> new NotFoundException("Notice", id));
        return NoticeResponse.from(notice);
    }

    @Transactional
    public NoticeResponse create(NoticeRequest request) {
        Notice notice = new Notice(
                request.title().trim(),
                request.body().trim(),
                request.audience(),
                request.pinned(),
                clock.instant()
        );
        Notice saved = noticeRepository.saveAndFlush(notice);
        auditService.record(
                AUDIT_NOTICE_PUBLISHED,
                "Notice",
                String.valueOf(saved.getId()),
                "Published notice '" + saved.getTitle() + "' for " + saved.getAudience()
        );
        eventPublisher.publishEvent(new NoticePublishedEvent(
                saved.getId(),
                saved.getTitle(),
                saved.getAudience()
        ));
        return NoticeResponse.from(saved);
    }

    @Transactional
    public NoticeResponse update(Long id, NoticeRequest request) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notice", id));
        notice.update(
                request.title().trim(),
                request.body().trim(),
                request.audience(),
                request.pinned()
        );
        Notice saved = noticeRepository.saveAndFlush(notice);
        return NoticeResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notice", id));
        String title = notice.getTitle();
        noticeRepository.delete(notice);
        auditService.record(
                AUDIT_NOTICE_DELETED,
                "Notice",
                String.valueOf(id),
                "Deleted notice '" + title + "'"
        );
    }

    List<NoticeAudience> visibleAudiencesForCurrentUser() {
        if (SecurityUtils.hasRole("ADMIN")) {
            return List.of(NoticeAudience.ALL, NoticeAudience.TEACHERS, NoticeAudience.STUDENTS);
        }
        if (SecurityUtils.hasRole("TEACHER")) {
            return List.of(NoticeAudience.ALL, NoticeAudience.TEACHERS);
        }
        if (SecurityUtils.hasRole("STUDENT")) {
            return List.of(NoticeAudience.ALL, NoticeAudience.STUDENTS);
        }
        return List.of();
    }
}
