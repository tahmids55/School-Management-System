package com.school.audit;

import com.school.audit.AuditDtos.AuditLogResponse;
import com.school.common.PageResponse;
import com.school.common.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Records and queries append-only audit entries.
 */
@Service
public class AuditService {

    public static final int MAX_ACTOR_LENGTH = 50;
    public static final int MAX_FIELD_LENGTH = 50;
    public static final int MAX_DETAILS_LENGTH = 1000;

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String entityType, String entityId, String details) {
        recordAs(SecurityUtils.currentUsernameOrSystem(), action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAs(String actor, String action, String entityType, String entityId, String details) {
        String normalizedActor = (actor == null || actor.isBlank()) ? SecurityUtils.SYSTEM_ACTOR : actor;
        AuditLog entry = new AuditLog(
                truncate(normalizedActor, MAX_ACTOR_LENGTH),
                truncate(action, MAX_FIELD_LENGTH),
                truncate(entityType, MAX_FIELD_LENGTH),
                truncate(entityId, MAX_FIELD_LENGTH),
                truncate(details, MAX_DETAILS_LENGTH),
                clock.instant()
        );
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String actor, String action, Pageable pageable) {
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);

        String trimmedActor = normalizeFilter(actor);
        String trimmedAction = normalizeFilter(action);

        Page<AuditLog> page;
        if (trimmedActor != null && trimmedAction != null) {
            page = auditLogRepository.findByActorIgnoreCaseAndAction(trimmedActor, trimmedAction, effectivePageable);
        } else if (trimmedActor != null) {
            page = auditLogRepository.findByActorIgnoreCase(trimmedActor, effectivePageable);
        } else if (trimmedAction != null) {
            page = auditLogRepository.findByAction(trimmedAction, effectivePageable);
        } else {
            page = auditLogRepository.findAll(effectivePageable);
        }

        return PageResponse.from(page, AuditLogResponse::from);
    }

    private static String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
