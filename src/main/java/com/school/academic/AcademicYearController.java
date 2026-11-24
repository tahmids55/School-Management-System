package com.school.academic;

import com.school.academic.AcademicDtos.AcademicYearRequest;
import com.school.academic.AcademicDtos.AcademicYearResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/academic-years")
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    public AcademicYearController(AcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<AcademicYearResponse>> list() {
        return ResponseEntity.ok(academicYearService.list());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/current")
    public ResponseEntity<AcademicYearResponse> current() {
        return ResponseEntity.ok(academicYearService.getCurrent());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<AcademicYearResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(academicYearService.get(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<AcademicYearResponse> create(@Valid @RequestBody AcademicYearRequest request) {
        AcademicYearResponse created = academicYearService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/academic-years/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<AcademicYearResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AcademicYearRequest request
    ) {
        return ResponseEntity.ok(academicYearService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/activate")
    public ResponseEntity<AcademicYearResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(academicYearService.activate(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        academicYearService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
