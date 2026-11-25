package com.school.student;

import com.school.common.PageResponse;
import com.school.student.StudentDtos.CreatedStudentResponse;
import com.school.student.StudentDtos.StatusRequest;
import com.school.student.StudentDtos.StudentRequest;
import com.school.student.StudentDtos.StudentResponse;
import com.school.student.StudentDtos.StudentSummary;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Role checks (RBAC) are here in @PreAuthorize; ownership checks (which student, which class) are in
 * StudentService via StudentAccessGuard. Export and all writes are ADMIN only, and there is no public
 * register endpoint: an admin registers students with POST. There is deliberately no DELETE:
 * PATCH /status is the soft delete.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /** Example: GET /students?q=rahim&classId=3&status=ACTIVE&page=0&size=20&sort=admissionNo */
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping
    public ResponseEntity<PageResponse<StudentSummary>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) StudentStatus status,
            @PageableDefault(size = 20, sort = "admissionNo") Pageable pageable
    ) {
        return ResponseEntity.ok(studentService.search(q, classId, status, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) StudentStatus status
    ) {
        byte[] csv = studentService.exportCsv(classId, status).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("students.csv").build().toString())
                .body(csv);
    }

    /** Any login; the service then allows ADMIN, the student themselves, or a teacher of their class. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.get(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CreatedStudentResponse> create(@Valid @RequestBody StudentRequest request) {
        CreatedStudentResponse created = studentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/students/" + created.student().id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return ResponseEntity.ok(studentService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<StudentResponse> changeStatus(
            @PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ResponseEntity.ok(studentService.changeStatus(id, request));
    }
}
