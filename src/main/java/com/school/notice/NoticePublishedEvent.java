package com.school.notice;

/**
 * Domain event published when an administrator creates a new notice.
 * Downstream listeners (such as F26 Notifications) subscribe to this event.
 */
public record NoticePublishedEvent(
        Long noticeId,
        String title,
        NoticeAudience audience
) {
}
