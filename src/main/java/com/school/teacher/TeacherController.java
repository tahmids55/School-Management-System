package com.school.teacher;

import com.school.common.PageResponse;
import com.school.teacher.TeacherDtos.CreatedTeacherResponse;
import com.school.teacher.TeacherDtos.StatusRequest;
import com.school.teacher.TeacherDtos.TeacherRequest;
import com.school.teacher.TeacherDtos.TeacherResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping
    public ResponseEntity<PageResponse<TeacherResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TeacherStatus status,
            @PageableDefault(size = 20, sort = "firstName") Pageable pageable
    ) {
        return ResponseEntity.ok(teacherService.list(q, status, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping("/{id}")
    public ResponseEntity<TeacherResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.get(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CreatedTeacherResponse> create(@Valid @RequestBody TeacherRequest request) {
        CreatedTeacherResponse created = teacherService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/teachers/" + created.teacher().id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TeacherResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TeacherRequest request
    ) {
        return ResponseEntity.ok(teacherService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<TeacherResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request
    ) {
        return ResponseEntity.ok(teacherService.updateStatus(id, request));
    }
}
