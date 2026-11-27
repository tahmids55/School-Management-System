package com.school.notification;

import com.school.common.PageResponse;
import com.school.common.SecurityUtils;
import com.school.common.exception.NotFoundException;
import com.school.notification.NotificationDtos.NotificationResponse;
import com.school.user.AppUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The signed-in user's own notifications (every query is filtered by their user id, so nobody can
 * read or mark someone else's), and {@link #notifyUsers} for the event listener.
 */
@Service
public class NotificationService {

    public static final int DEFAULT_PAGE_SIZE = 15;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "createdAt", "readAt", "title");

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository,
            AppUserRepository appUserRepository,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(Pageable pageable) {
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        for (Sort.Order order : effective.getSort()) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Invalid sort field: " + order.getProperty());
            }
        }
        return PageResponse.from(
                notificationRepository.findByUserId(currentUserId(), effective),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countUnread(currentUserId());
    }

    /** 404 for an unknown id AND for someone else's notification, so ids of others cannot be probed. */
    @Transactional
    public void markRead(Long id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, currentUserId())
                .orElseThrow(() -> new NotFoundException("Notification", id));
        notification.markRead(clock.instant());
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(currentUserId(), clock.instant());
    }

    /**
     * Gives every user the same notification, all rows in ONE new transaction.
     * REQUIRES_NEW because the listener runs AFTER the publisher's transaction committed:
     * that transaction cannot take new writes any more, so this work needs its own.
     * A user id that appears twice gets one notification.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyUsers(Collection<Long> userIds, String title, String message, String link) {
        Instant now = clock.instant();
        List<Notification> rows = new LinkedHashSet<>(userIds).stream()
                // getReferenceById: no SELECT per user, only the id is needed for the foreign key.
                .map(userId -> new Notification(appUserRepository.getReferenceById(userId), title, message, link, now))
                .toList();
        notificationRepository.saveAll(rows);
        return rows.size();
    }

    private static Long currentUserId() {
        return SecurityUtils.currentUserId()
                .orElseThrow(() -> new AccessDeniedException("No user is signed in"));
    }
}
