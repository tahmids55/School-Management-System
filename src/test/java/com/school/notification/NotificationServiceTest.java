package com.school.notification;

import com.school.common.exception.NotFoundException;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T04:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AppUserRepository appUserRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, appUserRepository,
                Clock.fixed(NOW, ZoneId.of("Asia/Dhaka")));
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("amina").claim("uid", 7L).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void markRead_someoneElsesNotification_is404WithExactMessage() {
        when(notificationRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Notification 42 was not found");
    }

    @Test
    void markRead_ownNotification_setsReadAtFromClock() {
        Notification notification = new Notification(mock(AppUser.class), "T", "M", null, NOW.minusSeconds(60));
        when(notificationRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(notification));

        service.markRead(42L);

        assertThat(notification.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void markAllRead_onlyTheCallersRows() {
        service.markAllRead();

        verify(notificationRepository).markAllRead(7L, NOW);
    }

    @Test
    void mine_unknownSortField_isRejected() {
        assertThatThrownBy(() -> service.mine(PageRequest.of(0, 15, Sort.by("passwordHash"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void notifyUsers_oneRowPerDistinctUser_sameTextAndTime() {
        when(appUserRepository.getReferenceById(1L)).thenReturn(mock(AppUser.class));
        when(appUserRepository.getReferenceById(2L)).thenReturn(mock(AppUser.class));

        int created = service.notifyUsers(List.of(1L, 2L, 1L), "New notice", "Sports day", "/notices");

        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(saved.capture());
        assertThat(created).isEqualTo(2);
        assertThat(saved.getValue()).hasSize(2)
                .allMatch(n -> n.getTitle().equals("New notice") && n.getCreatedAt().equals(NOW) && !n.isRead());
    }
}
