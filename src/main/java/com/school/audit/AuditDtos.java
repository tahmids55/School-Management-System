package com.school.audit;

import java.time.Instant;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogResponse(
            Long id,
            String actor,
            String action,
            String entityType,
            String entityId,
            String details,
            Instant createdAt
    ) {
        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getActor(),
                    log.getAction(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.getDetails(),
                    log.getCreatedAt()
            );
        }
    }
}
