package com.school;

import com.school.common.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpaWebConfigIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestIdFilter requestIdFilter;

    private MockMvc mvcWithoutSecurity;

    @BeforeEach
    void setupMvcWithoutSecurity() {
        mvcWithoutSecurity = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestIdFilter)
                .build();
    }

    @Test
    void rootAndUnknownExtensionlessPaths_returnIndexHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<!DOCTYPE html>")));

        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<!DOCTYPE html>")));

        mockMvc.perform(get("/students/42"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<!DOCTYPE html>")));
    }

    @Test
    void existingStaticFile_isServedDirectly() throws Exception {
        mockMvc.perform(get("/static-check.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ok")));
    }

    @Test
    void missingFilesAndDocsUnknownPaths_returnReal404() throws Exception {
        mockMvc.perform(get("/missing.js"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/assets/missing.css"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/swagger-ui/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<!DOCTYPE html>"))));

        mockMvc.perform(get("/v3/api-docs/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<!DOCTYPE html>"))));
    }

    @Test
    void unknownApiAndActuatorPaths_returnReal404InsteadOfIndexHtml() throws Exception {
        mvcWithoutSecurity.perform(get("/api"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<!DOCTYPE html>"))));

        mvcWithoutSecurity.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<!DOCTYPE html>"))));

        mvcWithoutSecurity.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<!DOCTYPE html>"))));

        mvcWithoutSecurity.perform(get("/actuator/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<!DOCTYPE html>"))));
    }
}
