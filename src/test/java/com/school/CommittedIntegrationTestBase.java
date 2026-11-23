package com.school;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for tests verifying committed transactions (concurrency, AFTER_COMMIT) with explicit cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class CommittedIntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.configure(registry);
    }

    @AfterEach
    void cleanDatabase() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename <> 'flyway_schema_history'
                """,
                String.class
        );
        if (!tables.isEmpty()) {
            String quoted = tables.stream()
                    .map(table -> "\"" + table + "\"")
                    .collect(Collectors.joining(", "));
            jdbcTemplate.execute("TRUNCATE TABLE " + quoted + " RESTART IDENTITY CASCADE");
        }
    }
}
