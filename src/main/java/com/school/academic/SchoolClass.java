package com.school.academic;

import com.school.common.BaseEntity;
import com.school.teacher.Teacher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A class section such as "Class 9 - A" inside one academic year, mapped to {@code school_classes}.
 * Many classes belong to one year (M:1), with an optional class teacher and subject assignments.
 */
@Getter
@Setter
@Entity
@Table(name = "school_classes")
public class SchoolClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 10)
    private String section;

    @Column(length = 20)
    private String room;

    @Column(nullable = false)
    private int capacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_teacher_id")
    private Teacher classTeacher;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "schoolClass", fetch = FetchType.LAZY)
    private List<ClassSubject> classSubjects = new ArrayList<>();

    protected SchoolClass() {
    }

    public SchoolClass(AcademicYear academicYear, String name, String section, String room, int capacity) {
        this.academicYear = academicYear;
        this.name = name;
        this.section = section;
        this.room = room;
        this.capacity = capacity;
    }

    public SchoolClass(
            AcademicYear academicYear,
            String name,
            String section,
            String room,
            int capacity,
            Teacher classTeacher
    ) {
        this(academicYear, name, section, room, capacity);
        this.classTeacher = classTeacher;
    }

    /** "Class 6 - A" */
    public String getDisplayName() {
        return name + " - " + section;
    }
}
