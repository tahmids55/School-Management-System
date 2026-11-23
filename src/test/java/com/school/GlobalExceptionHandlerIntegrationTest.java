package com.school;

import com.school.audit.AuditLog;
import com.school.audit.AuditLogRepository;
import com.school.common.PageResponse;
import com.school.common.RequestIdFilter;
import com.school.common.exception.AuthenticationFailedException;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.common.exception.PaymentFailedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandlerIntegrationTest.ProbeControllerConfig.class)
class GlobalExceptionHandlerIntegrationTest extends IntegrationTestBase {

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
    void validationError_returns400ProblemJsonWithErrorsAndRequestId() throws Exception {
        mvcWithoutSecurity.perform(post("/test-probe/validate")
                        .header("X-Request-Id", "req-validation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"age\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-validation-1"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Some fields are invalid"))
                .andExpect(jsonPath("$.requestId").value("req-validation-1"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.age").isNotEmpty());
    }

    @Test
    void malformedJson_returns400ProblemJson() throws Exception {
        mvcWithoutSecurity.perform(post("/test-probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Malformed JSON request body"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unknownSortField_returns400InsteadOf500() throws Exception {
        mockMvc.perform(get("/test-probe/sort")
                        .param("sort", "nonExistentField,asc")
                        .header("X-Request-Id", "req-sort-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-sort-1"))
                .andExpect(jsonPath("$.requestId").value("req-sort-1"))
                .andExpect(jsonPath("$.detail").value("Invalid sort field: nonExistentField"));
    }

    @Test
    void pageSizeIsCappedAt200() throws Exception {
        mockMvc.perform(get("/test-probe/sort").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    void typeMismatchMissingParamAndIllegalArgument_return400() throws Exception {
        mockMvc.perform(get("/test-probe/number").param("count", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/test-probe/number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/test-probe/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Bad argument value"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void customDomainExceptions_returnExpectedProblemStatuses() throws Exception {
        mockMvc.perform(get("/test-probe/401"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid token"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/test-probe/402"))
                .andExpect(status().isPaymentRequired())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Payment declined"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/test-probe/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Student 42 was not found"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/test-probe/409-conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Email already exists"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/test-probe/409-optimistic"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This record was changed by someone else. Reload and try again."))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/test-probe/409-integrity"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The data conflicts with existing records"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/test-probe/422"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Exam is already published"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void preAuthorizeAccessDenied_returns403WithExactMessage() throws Exception {
        mockMvc.perform(get("/test-probe/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void preAuthorizeAdminAllowed_returns200() throws Exception {
        mockMvc.perform(get("/test-probe/admin-only"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void unexpectedException_returns500WithoutInternals() throws Exception {
        mockMvc.perform(get("/test-probe/500"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Something went wrong. Please try again later."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("secret-internal-failure"))));
    }

    @TestConfiguration
    static class ProbeControllerConfig {

        @RestController
        @RequestMapping("/test-probe")
        static class ProbeController {

            private final AuditLogRepository auditLogRepository;

            ProbeController(AuditLogRepository auditLogRepository) {
                this.auditLogRepository = auditLogRepository;
            }

            @PostMapping("/validate")
            String validate(@Valid @RequestBody ProbeRequest request) {
                return request.name();
            }

            @GetMapping("/sort")
            PageResponse<AuditLog> sort(Pageable pageable) {
                return PageResponse.from(auditLogRepository.findAll(pageable));
            }

            @GetMapping("/number")
            int number(@RequestParam int count) {
                return count;
            }

            @GetMapping("/illegal-argument")
            void illegalArgument() {
                throw new IllegalArgumentException("Bad argument value");
            }

            @GetMapping("/401")
            void unauthorized() {
                throw new AuthenticationFailedException("Invalid token");
            }

            @GetMapping("/402")
            void paymentFailed() {
                throw new PaymentFailedException("Payment declined");
            }

            @PreAuthorize("hasRole('ADMIN')")
            @GetMapping("/admin-only")
            String adminOnly() {
                return "ok";
            }

            @GetMapping("/404")
            void notFound() {
                throw new NotFoundException("Student", 42L);
            }

            @GetMapping("/409-conflict")
            void conflict() {
                throw new ConflictException("Email already exists");
            }

            @GetMapping("/409-optimistic")
            void optimisticLock() {
                throw new ObjectOptimisticLockingFailureException(AuditLog.class, 1L);
            }

            @GetMapping("/409-integrity")
            void dataIntegrity() {
                throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
            }

            @GetMapping("/422")
            void businessRule() {
                throw new BusinessRuleException("Exam is already published");
            }

            @GetMapping("/500")
            void unexpected() {
                throw new IllegalStateException("secret-internal-failure");
            }
        }

        record ProbeRequest(@NotBlank String name, @Min(1) int age) {
        }
    }
}
