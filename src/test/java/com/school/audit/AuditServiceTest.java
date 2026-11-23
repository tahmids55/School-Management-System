package com.school.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

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
}
