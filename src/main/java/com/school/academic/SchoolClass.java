package com.school.academic;

import com.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A class section such as "Class 9 - A" inside one academic year, mapped to {@code school_classes}.
 * Many classes belong to one year (M:1). class_teacher_id is not mapped yet: F16 adds it.
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

    protected SchoolClass() {
    }

    public SchoolClass(AcademicYear academicYear, String name, String section, String room, int capacity) {
        this.academicYear = academicYear;
        this.name = name;
        this.section = section;
        this.room = room;
        this.capacity = capacity;
    }

    /** "Class 6 - A" */
    public String getDisplayName() {
        return name + " - " + section;
    }
}
