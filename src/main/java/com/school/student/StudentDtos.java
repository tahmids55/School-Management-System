package com.school.student;

import com.school.academic.SchoolClass;
import com.school.user.UserDtos.TemporaryPasswordResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class StudentDtos {

    private static final String PHONE = "^\\+?[0-9]{10,15}$";
    private static final String PHONE_MESSAGE = "must be 10-15 digits, optionally starting with +";

    private StudentDtos() {
    }

    /** createAccount = true also creates a STUDENT login (email required); only used by POST. */
    public record StudentRequest(
            @NotBlank @Size(max = 20) String admissionNo,
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotNull Gender gender,
            @NotNull @Past LocalDate dateOfBirth,
            @Email @Size(max = 120) String email,
            @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @Size(max = 255) String address,
            @NotBlank @Size(max = 120) String guardianName,
            @NotBlank @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String guardianPhone,
            @NotBlank @Size(max = 30) String guardianRelation,
            Long classId,
            @Min(1) @Max(500) Integer rollNo,
            @NotNull @PastOrPresent LocalDate admissionDate,
            boolean createAccount
    ) {
    }

    public record StudentResponse(
            Long id,
            String admissionNo,
            String firstName,
            String lastName,
            String fullName,
            Gender gender,
            LocalDate dateOfBirth,
            String email,
            String phone,
            String address,
            String guardianName,
            String guardianPhone,
            String guardianRelation,
            Long classId,
            String className,
            Integer rollNo,
            StudentStatus status,
            LocalDate admissionDate,
            String username
    ) {
        /** The student's class and user should already be loaded (join fetch). */
        public static StudentResponse from(Student student) {
            SchoolClass schoolClass = student.getSchoolClass();
            return new StudentResponse(
                    student.getId(),
                    student.getAdmissionNo(),
                    student.getFirstName(),
                    student.getLastName(),
                    student.getFullName(),
                    student.getGender(),
                    student.getDateOfBirth(),
                    student.getEmail(),
                    student.getPhone(),
                    student.getAddress(),
                    student.getGuardianName(),
                    student.getGuardianPhone(),
                    student.getGuardianRelation(),
                    schoolClass == null ? null : schoolClass.getId(),
                    schoolClass == null ? null : schoolClass.getDisplayName(),
                    student.getRollNo(),
                    student.getStatus(),
                    student.getAdmissionDate(),
                    student.getUser() == null ? null : student.getUser().getUsername()
            );
        }
    }

    public record StudentSummary(
            Long id,
            String admissionNo,
            String fullName,
            Integer rollNo,
            Long classId,
            String className,
            Gender gender,
            StudentStatus status,
            String guardianPhone
    ) {
        /** The student's class should already be loaded (entity graph / join fetch). */
        public static StudentSummary from(Student student) {
            SchoolClass schoolClass = student.getSchoolClass();
            return new StudentSummary(
                    student.getId(),
                    student.getAdmissionNo(),
                    student.getFullName(),
                    student.getRollNo(),
                    schoolClass == null ? null : schoolClass.getId(),
                    schoolClass == null ? null : schoolClass.getDisplayName(),
                    student.getGender(),
                    student.getStatus(),
                    student.getGuardianPhone()
            );
        }
    }

    public record StatusRequest(@NotNull StudentStatus status) {
    }

    /** account (username + temporary password, shown once) only when a login was created. */
    public record CreatedStudentResponse(
            StudentResponse student,
            TemporaryPasswordResponse account
    ) {
        public static CreatedStudentResponse from(StudentResponse student, TemporaryPasswordResponse account) {
            return new CreatedStudentResponse(student, account);
        }
    }
}
