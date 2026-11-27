package com.school.notification;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id,
            String title,
            String message,
            String link,
            boolean read,
            Instant createdAt
    ) {
        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getLink(),
                    notification.isRead(),
                    notification.getCreatedAt()
            );
        }
    }
}
