package com.school.exam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    @Query("""
            select e from Exam e
            join fetch e.academicYear
            where e.academicYear.id = :academicYearId
            order by e.startDate desc, e.id desc
            """)
    List<Exam> findByAcademicYearIdOrderByStartDateDescIdDesc(@Param("academicYearId") Long academicYearId);

    @Query("""
            select e from Exam e
            join fetch e.academicYear
            where e.academicYear.id = :academicYearId
              and e.status = :status
            order by e.endDate desc, e.id desc
            """)
    List<Exam> findByAcademicYearIdAndStatusOrderByEndDateDescIdDesc(
            @Param("academicYearId") Long academicYearId,
            @Param("status") ExamStatus status
    );

    @Query("select e from Exam e join fetch e.academicYear where e.id = :id")
    Optional<Exam> findByIdWithAcademicYear(@Param("id") Long id);

    boolean existsByAcademicYearIdAndNameIgnoreCase(Long academicYearId, String name);

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(Long academicYearId, String name, Long id);

    boolean existsByAcademicYearId(Long academicYearId);
}
