package com.school.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    /**
     * One SELECT for all classes of a year. "join fetch" loads each class's year in the same query,
     * so reading academicYear.getName() later does not fire one extra query per class (N+1).
     */
    @Query("select c from SchoolClass c join fetch c.academicYear"
            + " where c.academicYear.id = :yearId order by c.name, c.section")
    List<SchoolClass> findAllByYearWithYear(@Param("yearId") Long yearId);

    @Query("select c from SchoolClass c join fetch c.academicYear where c.id = :id")
    Optional<SchoolClass> findByIdWithYear(@Param("id") Long id);

    boolean existsByAcademicYearId(Long academicYearId);

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndSection(Long academicYearId, String name, String section);

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndSectionAndIdNot(
            Long academicYearId, String name, String section, Long id);

    /**
     * Students per class for many classes in ONE grouped query: rows of [classId, count].
     * Native SQL so this feature does not depend on the Student entity (F13).
     */
    @Query(value = "SELECT school_class_id, COUNT(*) FROM students"
            + " WHERE school_class_id IN (:classIds) GROUP BY school_class_id", nativeQuery = true)
    List<Object[]> countStudentsByClassIds(@Param("classIds") Collection<Long> classIds);

    @Query(value = "SELECT COUNT(*) FROM students WHERE school_class_id = :classId", nativeQuery = true)
    long countStudents(@Param("classId") Long classId);
}
