package com.school.notice;

import com.school.audit.AuditService;
import com.school.common.PageResponse;
import com.school.common.exception.NotFoundException;
import com.school.notice.NoticeDtos.NoticeRequest;
import com.school.notice.NoticeDtos.NoticeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-24T06:00:00Z");

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("Asia/Dhaka"));
        noticeService = new NoticeService(noticeRepository, auditService, eventPublisher, fixedClock);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_appliesRoleAudienceFilterAndDefaultPinnedNewestSort() {
        authenticateAs("t-1001", "ROLE_TEACHER");
        Notice notice = noticeWithId(10L, "Staff Meeting", "Room 201", NoticeAudience.TEACHERS, true, FIXED_NOW, "admin");
        when(noticeRepository.findByAudienceIn(any(), any())).thenReturn(new PageImpl<>(List.of(notice)));

        PageResponse<NoticeResponse> result = noticeService.list(PageRequest.of(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).author()).isEqualTo("admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<NoticeAudience>> audienceCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(noticeRepository).findByAudienceIn(audienceCaptor.capture(), pageableCaptor.capture());

        assertThat(audienceCaptor.getValue())
                .containsExactlyInAnyOrder(NoticeAudience.ALL, NoticeAudience.TEACHERS);
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("pinned"),
                Sort.Order.desc("publishedAt"),
                Sort.Order.desc("id")
        ));
    }

    @Test
    void list_studentSeesAllAndStudents_adminSeesAllThree() {
        authenticateAs("adm-2026-001", "ROLE_STUDENT");
        assertThat(noticeService.visibleAudiencesForCurrentUser())
                .containsExactlyInAnyOrder(NoticeAudience.ALL, NoticeAudience.STUDENTS);

        authenticateAs("admin", "ROLE_ADMIN");
        assertThat(noticeService.visibleAudiencesForCurrentUser())
                .containsExactlyInAnyOrder(NoticeAudience.ALL, NoticeAudience.TEACHERS, NoticeAudience.STUDENTS);
    }

    @Test
    void getById_throwsNotFoundWhenHiddenFromCurrentRole() {
        authenticateAs("adm-2026-001", "ROLE_STUDENT");
        when(noticeRepository.findByIdAndAudienceIn(eq(42L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.getById(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Notice 42 was not found");
    }

    @Test
    void create_setsClockPublishedAt_auditsAndPublishesDomainEvent() {
        authenticateAs("admin", "ROLE_ADMIN");
        when(noticeRepository.saveAndFlush(any(Notice.class))).thenAnswer(invocation -> {
            Notice saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            ReflectionTestUtils.setField(saved, "createdBy", "admin");
            return saved;
        });

        NoticeRequest request = new NoticeRequest(
                "  Sports Day  ",
                "Line 1\nLine 2",
                NoticeAudience.STUDENTS,
                true
        );

        NoticeResponse response = noticeService.create(request);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("Sports Day");
        assertThat(response.body()).isEqualTo("Line 1\nLine 2");
        assertThat(response.audience()).isEqualTo(NoticeAudience.STUDENTS);
        assertThat(response.pinned()).isTrue();
        assertThat(response.publishedAt()).isEqualTo(FIXED_NOW);
        assertThat(response.author()).isEqualTo("admin");

        verify(auditService).record(
                eq(NoticeService.AUDIT_NOTICE_PUBLISHED),
                eq("Notice"),
                eq("7"),
                any()
        );

        ArgumentCaptor<NoticePublishedEvent> eventCaptor = ArgumentCaptor.forClass(NoticePublishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new NoticePublishedEvent(7L, "Sports Day", NoticeAudience.STUDENTS));
    }

    @Test
    void update_modifiesFieldsAndPreservesPublishedAt() {
        Instant originalPublishedAt = Instant.parse("2026-11-20T03:00:00Z");
        Notice existing = noticeWithId(5L, "Old", "Old body", NoticeAudience.ALL, false, originalPublishedAt, "admin");
        when(noticeRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(noticeRepository.saveAndFlush(existing)).thenReturn(existing);

        NoticeResponse updated = noticeService.update(
                5L,
                new NoticeRequest("New Title", "Updated body", NoticeAudience.TEACHERS, true)
        );

        assertThat(updated.title()).isEqualTo("New Title");
        assertThat(updated.body()).isEqualTo("Updated body");
        assertThat(updated.audience()).isEqualTo(NoticeAudience.TEACHERS);
        assertThat(updated.pinned()).isTrue();
        assertThat(updated.publishedAt()).isEqualTo(originalPublishedAt);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void delete_removesNoticeAndRecordsAudit_orThrows404WhenMissing() {
        Notice existing = noticeWithId(9L, "Closure", "School closed", NoticeAudience.ALL, false, FIXED_NOW, "admin");
        when(noticeRepository.findById(9L)).thenReturn(Optional.of(existing));

        noticeService.delete(9L);

        verify(noticeRepository).delete(existing);
        verify(auditService).record(eq(NoticeService.AUDIT_NOTICE_DELETED), eq("Notice"), eq("9"), any());

        when(noticeRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> noticeService.delete(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Notice 99 was not found");
    }

    private void authenticateAs(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "n/a",
                        List.of(new SimpleGrantedAuthority(authority))
                )
        );
    }

    private Notice noticeWithId(
            Long id,
            String title,
            String body,
            NoticeAudience audience,
            boolean pinned,
            Instant publishedAt,
            String createdBy
    ) {
        Notice notice = new Notice(title, body, audience, pinned, publishedAt);
        ReflectionTestUtils.setField(notice, "id", id);
        ReflectionTestUtils.setField(notice, "createdBy", createdBy);
        return notice;
    }
}
