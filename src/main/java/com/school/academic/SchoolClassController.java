package com.school.academic;

import com.school.academic.AcademicDtos.ClassRequest;
import com.school.academic.AcademicDtos.ClassResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/classes")
public class SchoolClassController {

    private final SchoolClassService schoolClassService;

    public SchoolClassController(SchoolClassService schoolClassService) {
        this.schoolClassService = schoolClassService;
    }

    /** Without academicYearId: the current year's classes (empty when no year is current). */
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping
    public ResponseEntity<List<ClassResponse>> list(@RequestParam(required = false) Long academicYearId) {
        return ResponseEntity.ok(schoolClassService.list(academicYearId));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping("/{id}")
    public ResponseEntity<ClassResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(schoolClassService.get(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ClassResponse> create(@Valid @RequestBody ClassRequest request) {
        ClassResponse created = schoolClassService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/classes/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ClassResponse> update(@PathVariable Long id, @Valid @RequestBody ClassRequest request) {
        return ResponseEntity.ok(schoolClassService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        schoolClassService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
