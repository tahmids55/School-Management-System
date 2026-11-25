package com.school.academic;

import com.school.common.BaseEntity;
import com.school.teacher.Teacher;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Links a {@link SchoolClass} and a {@link Subject}, optionally assigning a {@link Teacher}.
 */
@Getter
@Setter
@Entity
@Table(name = "class_subjects")
public class ClassSubject extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    protected ClassSubject() {
    }

    public ClassSubject(SchoolClass schoolClass, Subject subject, Teacher teacher) {
        this.schoolClass = schoolClass;
        this.subject = subject;
        this.teacher = teacher;
    }
}
