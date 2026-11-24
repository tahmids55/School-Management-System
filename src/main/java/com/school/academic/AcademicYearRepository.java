package com.school.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    /** Newest first: latest start date on top. */
    List<AcademicYear> findAllByOrderByStartDateDescIdDesc();

    Optional<AcademicYear> findByCurrentTrue();

    boolean existsByCurrentTrue();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Clears the current flag on every year in one UPDATE. Runs before the new year is marked,
     * so the partial unique index never sees two current years.
     * clearAutomatically drops entities loaded earlier, so nobody keeps a stale flag or version.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AcademicYear y set y.current = false, y.version = y.version + 1 where y.current = true")
    int clearCurrent();

    /** Native SQL, so this feature does not depend on the class (F08) and exam (F23) entities. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM school_classes WHERE academic_year_id = :id)"
            + " OR EXISTS (SELECT 1 FROM exams WHERE academic_year_id = :id)", nativeQuery = true)
    boolean hasClassesOrExams(@Param("id") Long id);
}
