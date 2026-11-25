package com.school.student;

import com.school.student.StudentDtos.StudentSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The students of a class. Kept out of SchoolClassController so F16 can change that file in parallel.
 * ADMIN only in F13; F14 opens it to the class's teachers.
 */
@RestController
public class ClassRosterController {

    private final StudentService studentService;

    public ClassRosterController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/classes/{id}/students")
    public ResponseEntity<List<StudentSummary>> roster(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.roster(id));
    }
}
