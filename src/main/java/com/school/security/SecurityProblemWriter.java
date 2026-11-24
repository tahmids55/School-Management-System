package com.school.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

/**
 * Writes RFC 7807 problem+json responses directly from servlet security filters and handlers.
 */
public final class SecurityProblemWriter {

    private SecurityProblemWriter() {
    }

    public static void write(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            HttpStatus status,
            String detail
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("requestId", RequestIdFilter.currentRequestId());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
