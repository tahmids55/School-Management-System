package com.school.academic;

import com.school.academic.AcademicDtos.ClassSubjectRequest;
import com.school.academic.AcademicDtos.ClassSubjectResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/classes/{id}/subjects")
public class ClassSubjectController {

    private final ClassSubjectService classSubjectService;

    public ClassSubjectController(ClassSubjectService classSubjectService) {
        this.classSubjectService = classSubjectService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping
    public ResponseEntity<List<ClassSubjectResponse>> list(@PathVariable Long id) {
        return ResponseEntity.ok(classSubjectService.listForClass(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public ResponseEntity<ClassSubjectResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody ClassSubjectRequest request
    ) {
        return ResponseEntity.ok(classSubjectService.assign(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{subjectId}")
    public ResponseEntity<Void> remove(@PathVariable Long id, @PathVariable Long subjectId) {
        classSubjectService.remove(id, subjectId);
        return ResponseEntity.noContent().build();
    }
}
