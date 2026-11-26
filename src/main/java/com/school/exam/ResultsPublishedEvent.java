package com.school.exam;

/**
 * Domain event published when an exam's results are published (F29 publishes, F26 listens).
 */
public record ResultsPublishedEvent(
        Long examId,
        String examName
) {
}
