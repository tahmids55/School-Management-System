package com.school.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStateFilterTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonApiRequest_skipsDatabaseCheck() throws Exception {
        AccountStateFilter filter = new AccountStateFilter(appUserRepository, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/students");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(appUserRepository);
    }

    @Test
    void missingAccount_clearsContextAndReturns401() throws Exception {
        AccountStateFilter filter = new AccountStateFilter(appUserRepository, objectMapper);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("deleted-user", "n/a", List.of())
        );
        when(appUserRepository.findByUsernameIgnoreCase("deleted-user")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/students");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Session expired. Please log in again.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(filterChain);
    }

    @Test
    void disabledAccount_clearsContextAndReturns401() throws Exception {
        AccountStateFilter filter = new AccountStateFilter(appUserRepository, objectMapper);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t-1001", "n/a", List.of())
        );
        AppUser disabled = new AppUser("t-1001", "t@school.edu", "h", "Ayesha", Role.TEACHER);
        disabled.setEnabled(false);
        when(appUserRepository.findByUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(disabled));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("This account is disabled. Contact the administrator.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(filterChain);
    }

    @Test
    void mustChangePassword_blocksNormalApiWith403_butAllowsMeAndChangePassword() throws Exception {
        AccountStateFilter filter = new AccountStateFilter(appUserRepository, objectMapper);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t-1001", "n/a", List.of())
        );
        AppUser tempUser = new AppUser("t-1001", "t@school.edu", "h", "Ayesha", Role.TEACHER);
        tempUser.setMustChangePassword(true);
        when(appUserRepository.findByUsernameIgnoreCase("t-1001")).thenReturn(Optional.of(tempUser));

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("GET", "/api/v1/students");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(blockedRequest, blockedResponse, filterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(403);
        assertThat(blockedResponse.getContentAsString()).contains("Change your temporary password first");
        verifyNoInteractions(filterChain);

        for (String allowedPath : List.of(
                "/api/v1/auth/me",
                "/api/v1/auth/change-password",
                "/api/v1/auth/logout",
                "/api/v1/auth/refresh"
        )) {
            MockHttpServletRequest allowedRequest = new MockHttpServletRequest("POST", allowedPath);
            MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
            filter.doFilter(allowedRequest, allowedResponse, filterChain);
            verify(filterChain).doFilter(allowedRequest, allowedResponse);
        }
    }
}
