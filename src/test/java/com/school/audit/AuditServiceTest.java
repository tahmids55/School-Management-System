package com.school.audit;

import com.school.audit.AuditDtos.AuditLogResponse;
import com.school.common.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-23T12:00:00Z");

    @Mock
    private AuditLogRepository auditLogRepository;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneId.of("Asia/Dhaka"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void record_whenUnauthenticated_usesSystemActorAndFixedClock() {
        AuditService auditService = new AuditService(auditLogRepository, clock);

        auditService.record("CREATE", "Student", "42", "Created student");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo("system");
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getEntityType()).isEqualTo("Student");
        assertThat(saved.getEntityId()).isEqualTo("42");
        assertThat(saved.getDetails()).isEqualTo("Created student");
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void record_whenAuthenticated_usesSecurityContextUsername() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("taki.admin", "n/a", List.of())
        );
        AuditService auditService = new AuditService(auditLogRepository, clock);

        auditService.record("LOGIN_SUCCESS", "AppUser", "1", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo("taki.admin");
        assertThat(saved.getEntityId()).isEqualTo("1");
        assertThat(saved.getDetails()).isNull();
    }

    @Test
    void recordAs_truncatesActorAt50AndDetailsAt1000() {
        AuditService auditService = new AuditService(auditLogRepository, clock);
        String longActor = "a".repeat(75);
        String longDetails = "d".repeat(1200);

        auditService.recordAs(longActor, "UPDATE", "FeeInvoice", "99", longDetails);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).hasSize(50);
        assertThat(saved.getDetails()).hasSize(1000);
    }

    @Test
    void recordAs_whenActorIsBlank_defaultsToSystem() {
        AuditService auditService = new AuditService(auditLogRepository, clock);

        auditService.recordAs("   ", "LOGIN_FAILED", "AppUser", null, "Invalid password");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("system");
    }

    @Test
    void search_withoutFilters_usesDefaultNewestSortAndMapsResponse() {
        AuditService auditService = new AuditService(auditLogRepository, clock);
        AuditLog log = new AuditLog("admin", "NOTICE_PUBLISHED", "Notice", "5", "Published", FIXED_INSTANT);
        ReflectionTestUtils.setField(log, "id", 101L);
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(log)));

        PageResponse<AuditLogResponse> result = auditService.search("   ", null, PageRequest.of(0, 25));

        assertThat(result.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(101L);
            assertThat(item.actor()).isEqualTo("admin");
            assertThat(item.action()).isEqualTo("NOTICE_PUBLISHED");
            assertThat(item.entityType()).isEqualTo("Notice");
            assertThat(item.entityId()).isEqualTo("5");
            assertThat(item.details()).isEqualTo("Published");
            assertThat(item.createdAt()).isEqualTo(FIXED_INSTANT);
        });

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
    }

    @Test
    void search_dispatchesByActorActionOrBoth_withTrimmedValues() {
        AuditService auditService = new AuditService(auditLogRepository, clock);
        Pageable pageable = PageRequest.of(0, 25, Sort.by(Sort.Order.desc("createdAt")));
        when(auditLogRepository.findByActorIgnoreCase(eq("admin"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));
        when(auditLogRepository.findByAction(eq("LOGIN_SUCCESS"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));
        when(auditLogRepository.findByActorIgnoreCaseAndAction(eq("admin"), eq("LOGIN_SUCCESS"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        auditService.search("  admin  ", null, pageable);
        verify(auditLogRepository).findByActorIgnoreCase("admin", pageable);

        auditService.search(null, "  LOGIN_SUCCESS  ", pageable);
        verify(auditLogRepository).findByAction("LOGIN_SUCCESS", pageable);

        auditService.search(" admin ", " LOGIN_SUCCESS ", pageable);
        verify(auditLogRepository).findByActorIgnoreCaseAndAction("admin", "LOGIN_SUCCESS", pageable);
    }
}
