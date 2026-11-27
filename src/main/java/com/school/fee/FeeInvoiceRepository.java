package com.school.fee;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FeeInvoiceRepository extends JpaRepository<FeeInvoice, Long>, JpaSpecificationExecutor<FeeInvoice> {

    @Override
    @EntityGraph(attributePaths = {"student", "student.schoolClass"})
    Page<FeeInvoice> findAll(Specification<FeeInvoice> spec, Pageable pageable);

    @Query("select i from FeeInvoice i join fetch i.student s left join fetch s.schoolClass where i.id = :id")
    Optional<FeeInvoice> findByIdWithStudentAndClass(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from FeeInvoice i where i.id = :id")
    Optional<FeeInvoice> findByIdForUpdate(@Param("id") Long id);

    @Query("select i from FeeInvoice i join fetch i.student s left join fetch s.schoolClass"
            + " where s.id = :studentId order by i.dueDate desc, i.id desc")
    List<FeeInvoice> findAllByStudentIdWithDetails(@Param("studentId") Long studentId);

    boolean existsByInvoiceNo(String invoiceNo);

    @Query("select distinct i.student.id from FeeInvoice i"
            + " where i.student.id in :studentIds"
            + " and i.status <> com.school.fee.InvoiceStatus.CANCELLED"
            + " and lower(i.title) = lower(:title)")
    Set<Long> findStudentIdsWithActiveInvoiceTitle(
            @Param("studentIds") Collection<Long> studentIds,
            @Param("title") String title
    );

    @Query("select sum(i.amount - i.discount) as totalBilled, sum(i.amountPaid) as totalCollected"
            + " from FeeInvoice i where i.status <> com.school.fee.InvoiceStatus.CANCELLED")
    FeeTotalsProjection sumActiveTotals();

    @Query("select count(i) from FeeInvoice i"
            + " where i.status in (com.school.fee.InvoiceStatus.UNPAID, com.school.fee.InvoiceStatus.PARTIALLY_PAID)"
            + " and i.dueDate < :today")
    long countOverdue(@Param("today") LocalDate today);

    interface FeeTotalsProjection {
        BigDecimal getTotalBilled();

        BigDecimal getTotalCollected();
    }
}
