package com.school.exam;

import com.school.academic.Subject;
import com.school.common.BaseEntity;
import com.school.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "marks")
public class Mark extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "marks_obtained", precision = 5, scale = 2)
    private BigDecimal marksObtained;

    @Column(nullable = false)
    private boolean absent;

    protected Mark() {
    }

    public Mark(Exam exam, Student student, Subject subject, BigDecimal marksObtained, boolean absent) {
        this.exam = exam;
        this.student = student;
        this.subject = subject;
        this.absent = absent;
        this.marksObtained = absent ? null : marksObtained;
    }

    public void update(BigDecimal marksObtained, boolean absent) {
        this.absent = absent;
        this.marksObtained = absent ? null : marksObtained;
    }
}
