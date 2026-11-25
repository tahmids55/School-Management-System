package com.school.teacher;

import com.school.user.UserDtos.TemporaryPasswordResponse;
import com.school.user.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class TeacherDtos {

    private TeacherDtos() {
    }

    public record TeacherRequest(
            @NotBlank @Size(max = 20) String employeeNo,
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank
            @Pattern(
                    regexp = "^\\+?[0-9]{10,15}$",
                    message = "must be 10-15 digits, optionally starting with +"
            )
            String phone,
            @NotBlank @Size(max = 60) String designation,
            @NotNull @PastOrPresent LocalDate joiningDate
    ) {
    }

    public record TeacherResponse(
            Long id,
            String employeeNo,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String phone,
            String designation,
            LocalDate joiningDate,
            TeacherStatus status,
            String username
    ) {
        /** The teacher's user association must already be fetched when present to avoid N+1 queries. */
        public static TeacherResponse from(Teacher teacher) {
            String username = teacher.getUser() != null ? teacher.getUser().getUsername() : null;
            return new TeacherResponse(
                    teacher.getId(),
                    teacher.getEmployeeNo(),
                    teacher.getFirstName(),
                    teacher.getLastName(),
                    teacher.getFullName(),
                    teacher.getEmail(),
                    teacher.getPhone(),
                    teacher.getDesignation(),
                    teacher.getJoiningDate(),
                    teacher.getStatus(),
                    username
            );
        }
    }

    public record StatusRequest(
            @NotNull TeacherStatus status
    ) {
    }

    public record CreatedTeacherResponse(
            TeacherResponse teacher,
            TemporaryPasswordResponse account
    ) {
        public static CreatedTeacherResponse from(Teacher teacher, UserService.NewAccount account) {
            return new CreatedTeacherResponse(
                    TeacherResponse.from(teacher),
                    TemporaryPasswordResponse.from(account)
            );
        }
    }
}
