package com.school.notice;

import com.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "notices")
public class Notice extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeAudience audience;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected Notice() {
    }

    public Notice(String title, String body, NoticeAudience audience, boolean pinned, Instant publishedAt) {
        this.title = title;
        this.body = body;
        this.audience = audience;
        this.pinned = pinned;
        this.publishedAt = publishedAt;
    }

    public void update(String title, String body, NoticeAudience audience, boolean pinned) {
        this.title = title;
        this.body = body;
        this.audience = audience;
        this.pinned = pinned;
    }
}
