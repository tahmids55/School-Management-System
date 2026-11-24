package com.school.audit;

import com.school.CommittedIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceCommittedIntegrationTest extends CommittedIntegrationTestBase {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void record_joinsCallerTransaction_andRollsBackWithCaller() {
        transactionTemplate.executeWithoutResult(status -> {
            auditService.recordAs("taki", "CREATE", "Subject", "10", "Should roll back");
            status.setRollbackOnly();
        });

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void record_whenCallerTransactionCommits_persistsRow() {
        transactionTemplate.executeWithoutResult(status ->
                auditService.recordAs("taki", "CREATE", "Subject", "11", "Committed entry"));

        assertThat(auditLogRepository.findAll())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getActor()).isEqualTo("taki");
                    assertThat(entry.getAction()).isEqualTo("CREATE");
                    assertThat(entry.getEntityType()).isEqualTo("Subject");
                    assertThat(entry.getEntityId()).isEqualTo("11");
                    assertThat(entry.getDetails()).isEqualTo("Committed entry");
                    assertThat(entry.getCreatedAt()).isNotNull();
                });
    }
}
