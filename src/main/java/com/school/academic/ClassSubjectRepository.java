package com.school.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {

    @Query("""
            select cs from ClassSubject cs
            join fetch cs.subject s
            left join fetch cs.teacher
            where cs.schoolClass.id = :classId
            order by s.code asc
            """)
    List<ClassSubject> findAllByClassIdWithDetails(@Param("classId") Long classId);

    @Query("""
            select cs from ClassSubject cs
            join fetch cs.subject
            left join fetch cs.teacher
            where cs.schoolClass.id = :classId and cs.subject.id = :subjectId
            """)
    Optional<ClassSubject> findBySchoolClassIdAndSubjectIdWithDetails(
            @Param("classId") Long classId,
            @Param("subjectId") Long subjectId
    );

    boolean existsBySubjectId(Long subjectId);

    boolean existsBySchoolClassIdAndTeacherId(Long schoolClassId, Long teacherId);

    boolean existsBySchoolClassIdAndSubjectIdAndTeacherId(Long schoolClassId, Long subjectId, Long teacherId);

    @Query("select distinct cs.schoolClass.id from ClassSubject cs where cs.teacher.id = :teacherId")
    List<Long> findClassIdsByTeacherId(@Param("teacherId") Long teacherId);

    @Query("""
            select cs from ClassSubject cs
            join fetch cs.schoolClass c
            join fetch c.academicYear y
            join fetch cs.subject s
            where cs.teacher.id = :teacherId
            order by y.startDate desc, c.name asc, c.section asc, s.code asc
            """)
    List<ClassSubject> findAllByTeacherIdWithDetails(@Param("teacherId") Long teacherId);
}
