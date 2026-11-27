package com.school.notification;

import com.school.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * One in-app message for one user, mapped to {@code notifications}.
 * The table has no version or audit columns (a notification is written once and only marked read),
 * so it does NOT extend BaseEntity. Unread = {@code readAt} is null.
 */
@Getter
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(nullable = false, length = 500, updatable = false)
    private String message;

    @Column(length = 200, updatable = false)
    private String link;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(AppUser user, String title, String message, String link, Instant createdAt) {
        this.user = user;
        this.title = title;
        this.message = message;
        this.link = link;
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Marking twice keeps the first time. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
