package com.school.exam;

import com.school.academic.AcademicYear;
import com.school.common.BaseEntity;
import com.school.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "exams")
public class Exam extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status = ExamStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected Exam() {
    }

    public Exam(AcademicYear academicYear, String name, LocalDate startDate, LocalDate endDate) {
        this.academicYear = academicYear;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ExamStatus.DRAFT;
    }

    public void requireDraft() {
        if (this.status == ExamStatus.PUBLISHED) {
            throw new ConflictException(
                    "Results of " + this.name + " are published. Unpublish them before making changes."
            );
        }
    }

    public void publish(Instant instant) {
        this.status = ExamStatus.PUBLISHED;
        this.publishedAt = instant;
    }

    public void unpublish() {
        this.status = ExamStatus.DRAFT;
        this.publishedAt = null;
    }

    public void updateDetails(AcademicYear academicYear, String name, LocalDate startDate, LocalDate endDate) {
        requireDraft();
        this.academicYear = academicYear;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
