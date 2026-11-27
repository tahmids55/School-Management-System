package com.school.exam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MarkRepository extends JpaRepository<Mark, Long> {

    @Query("""
            select m from Mark m
            where m.exam.id = :examId
              and m.subject.id = :subjectId
              and m.student.id in :studentIds
            """)
    List<Mark> findByExamIdAndSubjectIdAndStudentIdIn(
            @Param("examId") Long examId,
            @Param("subjectId") Long subjectId,
            @Param("studentIds") Collection<Long> studentIds
    );

    @Query("""
            select m from Mark m
            where m.exam.id = :examId
              and m.student.id in :studentIds
            """)
    List<Mark> findByExamIdAndStudentIdIn(
            @Param("examId") Long examId,
            @Param("studentIds") Collection<Long> studentIds
    );
}
