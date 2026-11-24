package com.school.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    public record NoticeRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 5000) String body,
            @NotNull NoticeAudience audience,
            boolean pinned
    ) {
    }

    public record NoticeResponse(
            Long id,
            String title,
            String body,
            NoticeAudience audience,
            boolean pinned,
            Instant publishedAt,
            String author
    ) {
        public static NoticeResponse from(Notice notice) {
            return new NoticeResponse(
                    notice.getId(),
                    notice.getTitle(),
                    notice.getBody(),
                    notice.getAudience(),
                    notice.isPinned(),
                    notice.getPublishedAt(),
                    notice.getCreatedBy()
            );
        }
    }
}
