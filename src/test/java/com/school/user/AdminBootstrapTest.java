package com.school.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void run_whenNoAdminAndPasswordSet_createsNasimaAkterAdmin() {
        when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("StrongAdmin#123")).thenReturn("$2a$12$encodedHash");

        AdminBootstrap bootstrap = new AdminBootstrap(appUserRepository, passwordEncoder, "StrongAdmin#123");
        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());

        AppUser saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getEmail()).isEqualTo("admin@school.edu");
        assertThat(saved.getFullName()).isEqualTo("Nasima Akter");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$encodedHash");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.isMustChangePassword()).isFalse();
    }

    @Test
    void run_whenAdminAlreadyExists_doesNothing() {
        when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        AdminBootstrap bootstrap = new AdminBootstrap(appUserRepository, passwordEncoder, "StrongAdmin#123");
        bootstrap.run(new DefaultApplicationArguments());

        verify(appUserRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void run_whenPasswordBlank_doesNothing() {
        when(appUserRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        AdminBootstrap bootstrap = new AdminBootstrap(appUserRepository, passwordEncoder, "   ");
        bootstrap.run(new DefaultApplicationArguments());

        verify(appUserRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
