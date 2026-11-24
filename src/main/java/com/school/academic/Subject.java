package com.school.academic;

import com.school.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A subject such as "PHY101 Physics", mapped to the {@code subjects} table.
 */
@Getter
@Setter
@Entity
@Table(name = "subjects")
public class Subject extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "full_marks", nullable = false)
    private int fullMarks;

    @Column(name = "pass_marks", nullable = false)
    private int passMarks;

    protected Subject() {
    }

    public Subject(String code, String name, int fullMarks, int passMarks) {
        this.code = code;
        this.name = name;
        this.fullMarks = fullMarks;
        this.passMarks = passMarks;
    }
}
