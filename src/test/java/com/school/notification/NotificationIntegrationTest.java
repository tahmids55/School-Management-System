package com.school.notification;

import com.school.IntegrationTestBase;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F26 endpoints through the real API and database: own notifications only. */
class NotificationIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/v1/notifications";
    private static final Instant START = Instant.parse("2026-11-01T04:00:00Z");

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser amina;
    private AppUser babul;
    private String aminaToken;
    private String babulToken;
    private Notification babulsNotification;

    @BeforeEach
    void setUp() throws Exception {
        appUserRepository.save(user("admin", Role.ADMIN));
        appUserRepository.save(user("t-1001", Role.TEACHER));
        amina = appUserRepository.save(user("adm-001", Role.STUDENT));
        babul = appUserRepository.save(user("adm-002", Role.STUDENT));
        // Amina: 17 notifications, one per hour, the newest last. Babul: one of his own.
        for (int i = 1; i <= 17; i++) {
            notificationRepository.save(new Notification(amina, "Title " + i, "Message " + i, "/notices",
                    START.plusSeconds(3600L * i)));
        }
        babulsNotification = notificationRepository.save(new Notification(babul, "Babul only", "Private", null,
                START.plusSeconds(3600L * 100)));
        appUserRepository.flush();
        aminaToken = login("adm-001");
        babulToken = login("adm-002");
    }

    @Test
    void list_ownOnly_newestFirst_defaultPageOf15() throws Exception {
        perform(get(URL), aminaToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(15))
                .andExpect(jsonPath("$.content.length()").value(15))
                .andExpect(jsonPath("$.totalElements").value(17))
                .andExpect(jsonPath("$.content[0].title").value("Title 17"))
                .andExpect(jsonPath("$.content[0].message").value("Message 17"))
                .andExpect(jsonPath("$.content[0].link").value("/notices"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-11-01T21:00:00Z"))
                .andExpect(jsonPath("$.content[?(@.title == 'Babul only')]").isEmpty());

        perform(get(URL), babulToken)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").doesNotExist());
    }

    @Test
    void readOne_ownIs204_andLowersTheUnreadCount() throws Exception {
        perform(get(URL + "/unread-count"), aminaToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(17));
        Long newest = notificationRepository.findByUserId(amina.getId(),
                PageRequest.of(0, 1,
                        Sort.by("createdAt").descending())).getContent().get(0).getId();

        perform(post(URL + "/" + newest + "/read"), aminaToken).andExpect(status().isNoContent());
        // Reading again is harmless.
        perform(post(URL + "/" + newest + "/read"), aminaToken).andExpect(status().isNoContent());

        perform(get(URL + "/unread-count"), aminaToken).andExpect(jsonPath("$.count").value(16));
        perform(get(URL), aminaToken).andExpect(jsonPath("$.content[0].read").value(true));
    }

    @Test
    void cannotReadAnotherUsersNotification_404() throws Exception {
        perform(post(URL + "/" + babulsNotification.getId() + "/read"), aminaToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notification " + babulsNotification.getId() + " was not found"));
        perform(post(URL + "/999999/read"), aminaToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notification 999999 was not found"));

        perform(get(URL + "/unread-count"), babulToken).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void readAll_marksOnlyTheCallersNotifications() throws Exception {
        perform(post(URL + "/read-all"), aminaToken).andExpect(status().isNoContent());

        perform(get(URL + "/unread-count"), aminaToken).andExpect(jsonPath("$.count").value(0));
        perform(get(URL + "/unread-count"), babulToken).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void everyRole_isAllowed_toItsOwnNotifications() throws Exception {
        for (String username : new String[] {"admin", "t-1001", "adm-001"}) {
            String token = login(username);
            perform(get(URL), token).andExpect(status().isOk());
            perform(get(URL + "/unread-count"), token).andExpect(status().isOk());
            perform(post(URL + "/read-all"), token).andExpect(status().isNoContent());
        }
    }

    @Test
    void withoutLogin_everyEndpointIs401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL + "/1/read")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL + "/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void badInput_is400_not500() throws Exception {
        perform(get(URL).param("sort", "passwordHash"), aminaToken).andExpect(status().isBadRequest());
        perform(post(URL + "/abc/read"), aminaToken).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private AppUser user(String username, Role role) {
        return new AppUser(username, username + "@school.edu", passwordEncoder.encode("Password#1"),
                username + " Name", role);
    }

    private ResultActions perform(
            MockHttpServletRequestBuilder request, String token
    ) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "Password#1"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
