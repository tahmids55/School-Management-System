package com.school.student;

import com.school.academic.SchoolClass;
import com.school.common.BaseEntity;
import com.school.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A student record, mapped to {@code students}.
 * Optional login (1:1 users, F14 links it) and optional class (M:1 school_classes) with a roll number.
 */
@Getter
@Setter
@Entity
@Table(name = "students")
public class Student extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private AppUser user;

    @Column(name = "admission_no", nullable = false, unique = true, length = 20, updatable = false)
    private String admissionNo;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(unique = true, length = 120)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "guardian_name", nullable = false, length = 120)
    private String guardianName;

    @Column(name = "guardian_phone", nullable = false, length = 20)
    private String guardianPhone;

    @Column(name = "guardian_relation", nullable = false, length = 30)
    private String guardianRelation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_class_id")
    private SchoolClass schoolClass;

    @Column(name = "roll_no")
    private Integer rollNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    protected Student() {
    }

    /** A new student is always ACTIVE. The admission number can never change afterwards. */
    public Student(String admissionNo) {
        this.admissionNo = admissionNo;
    }

    /** "Rahim Uddin" */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return status == StudentStatus.ACTIVE;
    }
}
