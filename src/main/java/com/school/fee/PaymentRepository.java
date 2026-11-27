package com.school.fee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query(value = "SELECT CAST(pg_advisory_xact_lock(:keyHash) AS TEXT)", nativeQuery = true)
    String acquireAdvisoryLock(@Param("keyHash") long keyHash);

    @Query("select p from Payment p join fetch p.invoice where p.idempotencyKey = :key")
    Optional<Payment> findByIdempotencyKeyWithInvoice(@Param("key") String key);

    @Query("select p from Payment p join fetch p.invoice i where i.id = :invoiceId order by p.paidAt desc, p.id desc")
    List<Payment> findAllByInvoiceIdWithInvoice(@Param("invoiceId") Long invoiceId);
}
